package com.budgettracker.dto;

import com.budgettracker.model.Budget.BudgetPeriod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
    private BigDecimal spent;
    private BigDecimal remaining;
    private Double percentageUsed;
    private BudgetPeriod period;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isOverBudget;
}