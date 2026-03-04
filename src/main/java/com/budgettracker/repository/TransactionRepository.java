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

    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByUserIdOrderByDateDesc(Long userId);

    List<Transaction> findByUserIdAndCategoryId(Long userId, Long categoryId);

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

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
            "AND t.date BETWEEN :startDate AND :endDate " +
            "ORDER BY t.date DESC")
    List<Transaction> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
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
            "AND t.date BETWEEN :startDate AND :endDate")
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
            "GROUP BY t.category.id")
    List<Object[]> sumAmountGroupedByCategoryIds(
            @Param("userId") Long userId,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
            "AND t.pending = true")
    List<Transaction> findPendingTransactionsByUserId(@Param("userId") Long userId);

    List<Transaction> findByUserIdAndAccountType(Long userId, String accountType);

    List<Transaction> findByUserIdAndAccountSubtype(Long userId, String accountSubtype);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
            "AND (:accountType IS NULL OR t.accountType = :accountType) " +
            "AND (:accountSubtype IS NULL OR t.accountSubtype = :accountSubtype) " +
            "ORDER BY t.date DESC")
    List<Transaction> findByUserIdWithAccountFilters(
            @Param("userId") Long userId,
            @Param("accountType") String accountType,
            @Param("accountSubtype") String accountSubtype
    );

    /**
     * Unified filter query — all params nullable, any combination works.
     * Replaces the old separate methods that the controller's if/else chain
     * called exclusively, making filters mutually exclusive by accident.
     */
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
            "AND (:startDate IS NULL OR t.date >= :startDate) " +
            "AND (:endDate IS NULL OR t.date <= :endDate) " +
            "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
            "AND (:accountType IS NULL OR t.accountType = :accountType) " +
            "AND (:accountSubtype IS NULL OR t.accountSubtype = :accountSubtype) " +
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