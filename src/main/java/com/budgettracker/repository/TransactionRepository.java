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

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByUserIdOrderByDateDesc(Long userId);

    List<Transaction> findByUserIdAndCategoryId(Long userId, Long categoryId);

    Optional<Transaction> findByTellerTransactionId(String tellerTransactionId);

    boolean existsByTellerTransactionId(String tellerTransactionId);

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

    /**
     * Bulk version — returns spending totals for ALL categories in a single query.
     * Used by BudgetService to eliminate the N+1 problem in getBudgetSummary()
     * and mapToResponse() loops.
     *
     * Returns a list of Object[] where:
     *   [0] = categoryId (Long)
     *   [1] = total spent (BigDecimal)
     *
     * Note: only categories that have at least one transaction in the date range
     * appear in the result — categories with zero spend are absent (not zero).
     * The service handles this by defaulting missing entries to BigDecimal.ZERO.
     */
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

    // Account type filtering
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
}