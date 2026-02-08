package com.budgettracker.service;

import com.budgettracker.model.Budget;
import com.budgettracker.model.BudgetAlert;
import com.budgettracker.model.BudgetAlert.AlertType;
import com.budgettracker.repository.BudgetAlertRepository;
import com.budgettracker.repository.BudgetRepository;
import com.budgettracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetAlertRepository budgetAlertRepository;

    @Transactional
    public void checkBudgetAndNotify(Long userId, Long categoryId, LocalDate transactionDate) {
        // Find active budget for this category
        Optional<Budget> budgetOpt = budgetRepository.findActiveBudgetByCategoryAndDate(
                userId, categoryId, transactionDate);

        if (budgetOpt.isEmpty()) {
            return; // No budget set for this category
        }

        Budget budget = budgetOpt.get();

        // Calculate spent amount
        BigDecimal spent = transactionRepository.sumAmountByUserIdAndCategoryIdAndDateRange(
                userId, categoryId, budget.getStartDate(), budget.getEndDate());

        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        // Calculate percentage
        Double percentageUsed = budget.getAmount().compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(budget.getAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        // Check if we should send alerts
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        // Check for EXCEEDED alert (100%+)
        if (percentageUsed >= 100.0) {
            Optional<BudgetAlert> recentAlert = budgetAlertRepository.findRecentAlertByBudgetIdAndType(
                    budget.getId(), AlertType.EXCEEDED, oneDayAgo);

            if (recentAlert.isEmpty()) {
                sendBudgetAlert(budget, AlertType.EXCEEDED, spent);
            }
        }
        // Check for APPROACHING alert (80%+)
        else if (percentageUsed >= 80.0) {
            Optional<BudgetAlert> recentAlert = budgetAlertRepository.findRecentAlertByBudgetIdAndType(
                    budget.getId(), AlertType.APPROACHING, oneDayAgo);

            if (recentAlert.isEmpty()) {
                sendBudgetAlert(budget, AlertType.APPROACHING, spent);
            }
        }
    }

    private void sendBudgetAlert(Budget budget, AlertType alertType, BigDecimal spent) {
        // Save alert record
        BudgetAlert alert = BudgetAlert.builder()
                .user(budget.getUser())
                .budget(budget)
                .alertType(alertType)
                .build();

        budgetAlertRepository.save(alert);

        // Log notification (In production, this would send email/push notification)
        String message;
        if (alertType == AlertType.EXCEEDED) {
            message = String.format(
                    "⚠️ BUDGET EXCEEDED: You've spent $%.2f of your $%.2f budget for %s (%s period)",
                    spent, budget.getAmount(), budget.getCategory().getName(), budget.getPeriod()
            );
        } else {
            message = String.format(
                    "⚠️ BUDGET WARNING: You've used 80%% of your budget for %s ($%.2f / $%.2f) - %s period",
                    budget.getCategory().getName(), spent, budget.getAmount(), budget.getPeriod()
            );
        }

        log.warn("NOTIFICATION for user {}: {}", budget.getUser().getId(), message);

        // TODO: Implement actual notification sending (email, SMS, push notification)
        // For now, we just log it
    }
}