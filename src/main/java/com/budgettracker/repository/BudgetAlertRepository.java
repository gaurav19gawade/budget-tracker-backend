package com.budgettracker.repository;

import com.budgettracker.model.BudgetAlert;
import com.budgettracker.model.BudgetAlert.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetAlertRepository extends JpaRepository<BudgetAlert, Long> {

    List<BudgetAlert> findByUserId(Long userId);

    List<BudgetAlert> findByBudgetId(Long budgetId);

    // Used in NotificationService to check if alert was recently sent
    @Query("SELECT ba FROM BudgetAlert ba WHERE ba.budget.id = :budgetId " +
            "AND ba.alertType = :alertType " +
            "AND ba.sentAt >= :since")
    Optional<BudgetAlert> findRecentAlertByBudgetIdAndType(
            @Param("budgetId") Long budgetId,
            @Param("alertType") AlertType alertType,
            @Param("since") LocalDateTime since
    );

    @Query("SELECT ba FROM BudgetAlert ba WHERE ba.user.id = :userId " +
            "AND ba.sentAt >= :since " +
            "ORDER BY ba.sentAt DESC")
    List<BudgetAlert> findRecentAlertsByUserId(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );
}