package com.budgettracker.repository;

import com.budgettracker.model.Budget;
import com.budgettracker.model.Budget.BudgetPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserId(Long userId);

    List<Budget> findByUserIdAndPeriod(Long userId, BudgetPeriod period);

    // Used in BudgetService.createBudget() to check for duplicates
    Optional<Budget> findByUserIdAndCategoryIdAndPeriodAndStartDate(
            Long userId,
            Long categoryId,
            BudgetPeriod period,
            LocalDate startDate
    );

    // Used in BudgetService.getActiveBudgets() and BudgetService.getBudgetSummary()
    @Query("SELECT b FROM Budget b WHERE b.user.id = :userId " +
            "AND b.startDate <= :date AND b.endDate >= :date")
    List<Budget> findActiveBudgetsByUserIdAndDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date
    );

    // Used in NotificationService.checkBudgetAndNotify()
    @Query("SELECT b FROM Budget b WHERE b.user.id = :userId " +
            "AND b.category.id = :categoryId " +
            "AND b.startDate <= :date AND b.endDate >= :date")
    Optional<Budget> findActiveBudgetByCategoryAndDate(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("date") LocalDate date
    );
}