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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final TransactionRepository transactionRepository;

    public AnalyticsSummaryResponse getSummary(Long userId, LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = transactionRepository
                .findByUserIdAndDateRange(userId, startDate, endDate);

        BigDecimal totalSpent = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsSummaryResponse.builder()
                .totalSpent(totalSpent)
                .totalTransactions(BigDecimal.valueOf(transactions.size()))
                .byCategory(buildCategorySpend(transactions, totalSpent))
                .byDay(buildDailySpend(transactions, startDate, endDate))
                .topMerchants(buildTopMerchants(transactions))
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
                .map(e -> MerchantSpend.builder()
                        .merchantName(e.getKey())
                        .amount(e.getValue().stream()
                                .map(Transaction::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .transactionCount(e.getValue().size())
                        .build())
                .sorted(Comparator.comparing(MerchantSpend::getAmount).reversed())
                .limit(8)
                .collect(Collectors.toList());
    }
}
