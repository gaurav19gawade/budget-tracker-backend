package com.budgettracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyOverviewResponse {

    private String     month;        // "March 2026"
    private String     monthShort;   // "Mar 2026"
    private BigDecimal earned;       // sum of credit transactions this month
    private BigDecimal spent;        // sum of debit transactions (excl. isExcluded categories)
    private BigDecimal saved;        // earned - spent (can be negative)
    private boolean    isSaving;     // saved >= 0
}
