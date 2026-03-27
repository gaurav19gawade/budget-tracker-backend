package com.budgettracker.repository;

import com.budgettracker.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // JOIN FETCH category on all list queries so mapToResponse() can access
    // category.getName() without a live Hibernate session (open-in-view=false).

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category WHERE t.user.id = :userId ORDER BY t.date DESC")
    List<Transaction> findByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category WHERE t.user.id = :userId ORDER BY t.date DESC")
    List<Transaction> findByUserIdOrderByDateDesc(@Param("userId") Long userId);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category " +
            "WHERE t.user.id = :userId AND t.category.id = :categoryId")
    List<Transaction> findByUserIdAndCategoryId(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

    Optional<Transaction> findByTellerTransactionId(String tellerTransactionId);

    boolean existsByTellerTransactionId(String tellerTransactionId);

    /**
     * Fetches all known Teller transaction IDs for a given enrollment in one query.
     * Used in syncTransactions() to do duplicate detection in memory (Set.contains)
     * instead of firing existsByTellerTransactionId() once per row in a loop.
     */
    @Query("SELECT t.tellerTransactionId FROM Transaction t " +
            "WHERE t.tellerEnrollment.id = :enrollmentId " +
            "AND t.tellerTransactionId IS NOT NULL")
    Set<String> findExistingTellerTransactionIds(@Param("enrollmentId") Long enrollmentId);

    /**
     * Sums real income (credit) transactions for a user within a date range.
     * Only counts depository account credits (salary, rental income) that have
     * a known non-excluded category. CC credits are refunds, not income.
     * Uncategorized credits are excluded — unknown = don't assume income.
     * Used by BudgetService to compute net cash flow on the dashboard.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId " +
            "AND t.transactionType = 'credit' " +
            "AND t.accountType = 'depository' " +
            "AND t.category IS NOT NULL " +
            "AND (t.category.isExcluded IS NULL OR t.category.isExcluded = false) " +
            "AND t.date BETWEEN :startDate AND :endDate")
    BigDecimal sumCreditTransactions(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Deletes all bank-synced (non-manual) transactions for an enrollment.
     * Used by force-resync to wipe stale data before a clean re-import.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Transaction t WHERE t.tellerEnrollment.id = :enrollmentId AND t.isManual = false")
    int deleteByTellerEnrollmentIdAndIsManualFalse(@Param("enrollmentId") Long enrollmentId);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category " +
            "WHERE t.user.id = :userId " +
            "AND t.date BETWEEN :startDate AND :endDate " +
            "ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category " +
            "WHERE t.user.id = :userId " +
            "AND t.category.id = :categoryId " +
            "AND t.date BETWEEN :startDate AND :endDate")
    List<Transaction> findByUserIdAndCategoryIdAndDateRange(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Used in NotificationService — single category spend check
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId " +
            "AND t.category.id = :categoryId " +
            "AND t.date BETWEEN :startDate AND :endDate " +
            "AND (t.transactionType IS NULL OR t.transactionType <> 'credit') " +
            "AND (t.category.isExcluded IS NULL OR t.category.isExcluded = false) " +
            "AND t.amount > 0")
    BigDecimal sumAmountByUserIdAndCategoryIdAndDateRange(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Used in BudgetService to eliminate N+1 in getBudgetSummary
    @Query("SELECT t.category.id, COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId " +
            "AND t.category.id IN :categoryIds " +
            "AND t.date BETWEEN :startDate AND :endDate " +
            "AND (t.transactionType IS NULL OR t.transactionType <> 'credit') " +
            "AND (t.category.isExcluded IS NULL OR t.category.isExcluded = false) " +
            "AND t.amount > 0 " +
            "GROUP BY t.category.id")
    List<Object[]> sumAmountGroupedByCategoryIds(
            @Param("userId") Long userId,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category " +
            "WHERE t.user.id = :userId AND t.pending = true")
    List<Transaction> findPendingTransactionsByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category " +
            "WHERE t.user.id = :userId AND t.accountType = :accountType")
    List<Transaction> findByUserIdAndAccountType(@Param("userId") Long userId, @Param("accountType") String accountType);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category " +
            "WHERE t.user.id = :userId AND t.accountSubtype = :accountSubtype")
    List<Transaction> findByUserIdAndAccountSubtype(@Param("userId") Long userId, @Param("accountSubtype") String accountSubtype);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category " +
            "WHERE t.user.id = :userId " +
            "AND (CAST(:accountType AS string) IS NULL OR t.accountType = :accountType) " +
            "AND (CAST(:accountSubtype AS string) IS NULL OR t.accountSubtype = :accountSubtype) " +
            "ORDER BY t.date DESC")
    List<Transaction> findByUserIdWithAccountFilters(
            @Param("userId") Long userId,
            @Param("accountType") String accountType,
            @Param("accountSubtype") String accountSubtype
    );

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.category " +
            "WHERE t.user.id = :userId " +
            "AND (CAST(:startDate AS date) IS NULL OR t.date >= :startDate) " +
            "AND (CAST(:endDate AS date) IS NULL OR t.date <= :endDate) " +
            "AND (CAST(:categoryId AS long) IS NULL OR t.category.id = :categoryId) " +
            "AND (CAST(:accountType AS string) IS NULL OR t.accountType = :accountType) " +
            "AND (CAST(:accountSubtype AS string) IS NULL OR t.accountSubtype = :accountSubtype) " +
            "ORDER BY t.date DESC")
    List<Transaction> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("categoryId") Long categoryId,
            @Param("accountType") String accountType,
            @Param("accountSubtype") String accountSubtype
    );
}
