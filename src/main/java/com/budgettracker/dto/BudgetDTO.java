package com.budgettracker.dto;

import com.budgettracker.model.Budget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDTO {

    private Long id;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private String categoryName;

    @NotNull(message = "Budget amount is required")
    @Positive(message = "Budget amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Period is required")
    private Budget.BudgetPeriod period;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    // Calculated fields
    private BigDecimal spent;
    private BigDecimal remaining;
    private Double percentageUsed;
    private Boolean isOverBudget;
}