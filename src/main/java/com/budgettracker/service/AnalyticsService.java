package com.budgettracker.service;

import com.budgettracker.dto.AnalyticsSummaryResponse;
import com.budgettracker.dto.AnalyticsSummaryResponse.*;
import com.budgettracker.model.Transaction;
import com.budgettracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final TransactionRepository transactionRepository;

    public AnalyticsSummaryResponse getSummary(Long userId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = transactionRepository
                .findByUserIdAndDateRange(userId, startDate, endDate);

        // Only count debits (money out) as "spend".
        // We count debits from ALL account types — credit card purchases ARE real expenses.
        // Excluded categories (Transfer, Investments, Credit Card Payment) are dropped.
        // Uncategorized debits are included — unknown spending is still spending.
        // Exception: savings account debits (e.g. "Online Transfer to CHK") are transfers.
        List<Transaction> debits = transactions.stream()
                .filter(tx -> !"credit".equalsIgnoreCase(tx.getTransactionType()))
                .filter(tx -> tx.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .filter(tx -> !"savings".equalsIgnoreCase(tx.getAccountSubtype()))
                .filter(tx -> tx.getCategory() == null
                        || tx.getCategory().getIsExcluded() == null
                        || !tx.getCategory().getIsExcluded())
                .toList();

        BigDecimal totalSpent = debits.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Real income = credits on DEPOSITORY accounts (checking/savings) only.
        //
        // Why depository-only:
        //   Credit-card credits are ALWAYS refunds/returns — never salary or rental income.
        //   This eliminates the entire Sapphire Reserve refund pollution problem.
        //
        // Null category = still counts as income.
        //   Uncategorized payroll (GE Healthcare, Ernst & Young) must not be excluded
        //   just because the user hasn't categorized it yet.
        //
        // Explicitly excluded categories (Transfer, Investments, Credit Card Payment)
        //   are the only things dropped. isExcluded=true is the opt-out signal.
        List<Transaction> credits = transactions.stream()
                .filter(tx -> "credit".equalsIgnoreCase(tx.getTransactionType()))
                .filter(tx -> "depository".equalsIgnoreCase(tx.getAccountType()))
                .filter(tx -> tx.getCategory() == null
                        || tx.getCategory().getIsExcluded() == null
                        || !tx.getCategory().getIsExcluded())
                .toList();

        return AnalyticsSummaryResponse.builder()
                .totalSpent(totalSpent)
                .totalTransactions(BigDecimal.valueOf(debits.size()))
                .byCategory(buildCategorySpend(debits, totalSpent))
                .byDay(buildDailySpend(debits, startDate, endDate))
                .topMerchants(buildTopMerchants(debits))
                .byMonth(buildMonthlySpend(debits, credits, startDate, endDate))
                .build();
    }

    private List<CategorySpend> buildCategorySpend(List<Transaction> transactions, BigDecimal totalSpent) {
        // Group by category, sum amounts
        Map<Long, List<Transaction>> byCat = transactions.stream()
                .filter(tx -> tx.getCategory() != null)
                .collect(Collectors.groupingBy(tx -> tx.getCategory().getId()));

        List<CategorySpend> result = new ArrayList<>();
        for (Map.Entry<Long, List<Transaction>> entry : byCat.entrySet()) {
            var cat = entry.getValue().get(0).getCategory();
            BigDecimal catTotal = entry.getValue().stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            double pct = totalSpent.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : catTotal.divide(totalSpent, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();

            result.add(CategorySpend.builder()
                    .categoryId(cat.getId())
                    .categoryName(cat.getName())
                    .categoryIcon(cat.getIcon())
                    .categoryColor(cat.getColor())
                    .amount(catTotal)
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .build());
        }

        // Sort by amount descending
        result.sort(Comparator.comparing(CategorySpend::getAmount).reversed());
        return result;
    }

    private List<DailySpend> buildDailySpend(List<Transaction> transactions,
                                             LocalDate startDate, LocalDate endDate) {
        // Build map of date → total spend
        Map<LocalDate, BigDecimal> dailyMap = transactions.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getDate,
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        // Fill every day in range (including zero-spend days for a continuous line)
        List<DailySpend> result = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            result.add(DailySpend.builder()
                    .date(current.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .amount(dailyMap.getOrDefault(current, BigDecimal.ZERO))
                    .build());
            current = current.plusDays(1);
        }
        return result;
    }

    private List<MerchantSpend> buildTopMerchants(List<Transaction> transactions) {
        // Group by merchant name, sum and count
        Map<String, List<Transaction>> byMerchant = transactions.stream()
                .filter(tx -> tx.getMerchantName() != null && !tx.getMerchantName().isBlank())
                .collect(Collectors.groupingBy(tx ->
                        tx.getMerchantName().length() > 30
                                ? tx.getMerchantName().substring(0, 30)
                                : tx.getMerchantName()
                ));

        return byMerchant.entrySet().stream()
                .map(e -> {
                    List<Transaction> txns = e.getValue();
                    BigDecimal total = txns.stream()
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Find the most common category for this merchant
                    String catName = null;
                    String catIcon = null;
                    Optional<Map.Entry<String, Long>> topCat = txns.stream()
                            .filter(tx -> tx.getCategory() != null)
                            .collect(Collectors.groupingBy(
                                    tx -> tx.getCategory().getName(),
                                    Collectors.counting()))
                            .entrySet().stream()
                            .max(Map.Entry.comparingByValue());
                    if (topCat.isPresent()) {
                        String catNameFinal = topCat.get().getKey();
                        catName = catNameFinal;
                        catIcon = txns.stream()
                                .filter(tx -> tx.getCategory() != null
                                        && catNameFinal.equals(tx.getCategory().getName()))
                                .findFirst()
                                .map(tx -> tx.getCategory().getIcon())
                                .orElse(null);
                    }

                    return MerchantSpend.builder()
                            .merchantName(e.getKey())
                            .amount(total)
                            .transactionCount(txns.size())
                            .categoryName(catName)
                            .categoryIcon(catIcon)
                            .build();
                })
                .sorted(Comparator.comparing(MerchantSpend::getAmount).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<MonthlySpend> buildMonthlySpend(List<Transaction> debits,
                                                 List<Transaction> credits,
                                                 LocalDate startDate, LocalDate endDate) {
        // Group debits by year-month
        Map<java.time.YearMonth, BigDecimal> debitByMonth = debits.stream()
                .collect(Collectors.groupingBy(
                        tx -> java.time.YearMonth.from(tx.getDate()),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        // Group credits by year-month
        Map<java.time.YearMonth, BigDecimal> creditByMonth = credits.stream()
                .collect(Collectors.groupingBy(
                        tx -> java.time.YearMonth.from(tx.getDate()),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));

        // Enumerate every month in range
        java.time.YearMonth startMonth = java.time.YearMonth.from(startDate);
        java.time.YearMonth endMonth   = java.time.YearMonth.from(endDate);
        List<MonthlySpend> result = new ArrayList<>();

        java.time.YearMonth cursor = startMonth;
        while (!cursor.isAfter(endMonth)) {
            result.add(MonthlySpend.builder()
                    .month(cursor.toString())
                    .label(cursor.getMonth().getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.US))
                    .spent(debitByMonth.getOrDefault(cursor, BigDecimal.ZERO))
                    .income(creditByMonth.getOrDefault(cursor, BigDecimal.ZERO))
                    .build());
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    public com.budgettracker.dto.MonthlyOverviewResponse getMonthlyOverview(Long userId) {
        return getMonthlyOverview(userId, null, null);
    }

    public com.budgettracker.dto.MonthlyOverviewResponse getMonthlyOverview(
            Long userId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate start = startDate != null ? startDate : today.withDayOfMonth(1);
        java.time.LocalDate end   = endDate   != null ? endDate   : today.withDayOfMonth(today.lengthOfMonth());

        List<Transaction> transactions = transactionRepository
                .findByUserIdAndDateRange(userId, start, end);

        // Earned = depository credits only (CC credits are refunds, not income).
        // Null category still counts — uncategorized payroll is still income.
        // Only explicitly excluded categories (Transfer, Investments) are dropped.
        BigDecimal earned = transactions.stream()
                .filter(tx -> "credit".equalsIgnoreCase(tx.getTransactionType()))
                .filter(tx -> "depository".equalsIgnoreCase(tx.getAccountType()))
                .filter(tx -> tx.getCategory() == null
                        || tx.getCategory().getIsExcluded() == null
                        || !tx.getCategory().getIsExcluded())
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Spent = debit transactions, excluding transfer/excluded categories and savings transfers
        BigDecimal spent = transactions.stream()
                .filter(tx -> !"credit".equalsIgnoreCase(tx.getTransactionType()))
                .filter(tx -> tx.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .filter(tx -> !"savings".equalsIgnoreCase(tx.getAccountSubtype()))
                .filter(tx -> tx.getCategory() == null
                        || tx.getCategory().getIsExcluded() == null
                        || !tx.getCategory().getIsExcluded())
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saved = earned.subtract(spent);

        // Label: if same month use "March 2026", otherwise use date range "Feb 25 – Mar 27"
        boolean sameMonth = start.getMonth() == end.getMonth() && start.getYear() == end.getYear();
        String month = sameMonth
                ? start.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) + " " + start.getYear()
                : start.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")) + " – " + end.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"));
        String monthShort = sameMonth
                ? start.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US) + " " + start.getYear()
                : start.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")) + " – " + end.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"));

        return com.budgettracker.dto.MonthlyOverviewResponse.builder()
                .month(month)
                .monthShort(monthShort)
                .earned(earned)
                .spent(spent)
                .saved(saved)
                .isSaving(saved.compareTo(BigDecimal.ZERO) >= 0)
                .build();
    }
}
