package com.budgettracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingReportDTO {

    private LocalDate startDate;
    private LocalDate endDate;
    private String period; // "WEEKLY" or "MONTHLY"

    private BigDecimal totalSpent;
    private Integer totalTransactions;

    private List<CategorySpendingDTO> categoryBreakdown;

    // Top merchants
    private List<MerchantSpendingDTO> topMerchants;
}