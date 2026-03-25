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
    private List<CategorySpend>  byCategory;
    private List<DailySpend>     byDay;
    private List<MerchantSpend>  topMerchants;
    private List<MonthlySpend>   byMonth;      // month-over-month trend

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
        private String     categoryName;   // top category for this merchant
        private String     categoryIcon;
        private BigDecimal amount;
        private int        transactionCount;
    }

    /**
     * One entry per calendar month in the query window.
     * Used for month-over-month comparison chart.
     */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MonthlySpend {
        private String     month;      // "2026-02", "2026-03"
        private String     label;      // "Feb", "Mar"
        private BigDecimal spent;
        private BigDecimal income;
    }
}
