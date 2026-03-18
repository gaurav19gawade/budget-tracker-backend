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
public class AnalyticsSummaryResponse {

    private BigDecimal totalSpent;
    private BigDecimal totalTransactions;
    private List<CategorySpend> byCategory;
    private List<DailySpend>    byDay;
    private List<MerchantSpend> topMerchants;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategorySpend {
        private Long       categoryId;
        private String     categoryName;
        private String     categoryIcon;
        private String     categoryColor;
        private BigDecimal amount;
        private double     percentage;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailySpend {
        private String     date;   // "2026-03-01"
        private BigDecimal amount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MerchantSpend {
        private String     merchantName;
        private BigDecimal amount;
        private int        transactionCount;
    }
}
