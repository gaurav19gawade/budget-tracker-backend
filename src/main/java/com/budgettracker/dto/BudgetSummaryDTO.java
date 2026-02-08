package com.budgettracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSummaryDTO {

    private BigDecimal totalBudget;
    private BigDecimal totalSpent;
    private BigDecimal totalRemaining;
    private Double overallPercentageUsed;
    private Integer categoriesOverBudget;
    private List<BudgetDTO> budgets;
}