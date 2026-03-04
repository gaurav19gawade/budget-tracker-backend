package com.budgettracker.service;

import com.budgettracker.dto.BudgetRequest;
import com.budgettracker.dto.BudgetResponse;
import com.budgettracker.dto.BudgetSummaryResponse;
import com.budgettracker.exception.BadRequestException;
import com.budgettracker.exception.ResourceNotFoundException;
import com.budgettracker.model.Budget;
import com.budgettracker.model.Budget.BudgetPeriod;
import com.budgettracker.model.Category;
import com.budgettracker.model.User;
import com.budgettracker.repository.BudgetRepository;
import com.budgettracker.repository.CategoryRepository;
import com.budgettracker.repository.TransactionRepository;
import com.budgettracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public BudgetResponse createBudget(Long userId, BudgetRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }

        budgetRepository.findByUserIdAndCategoryIdAndPeriodAndStartDate(
                userId, request.getCategoryId(), request.getPeriod(), request.getStartDate()
        ).ifPresent(existing -> {
            throw new BadRequestException("Budget already exists for this category and period");
        });

        Budget budget = Budget.builder()
                .user(user)
                .category(category)
                .amount(request.getAmount())
                .period(request.getPeriod())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        budget = budgetRepository.save(budget);
        log.info("Created budget for category {} for user {}", category.getName(), userId);

        // Single budget — individual spend query is fine here (not a loop)
        BigDecimal spent = fetchSpent(userId, budget.getCategory().getId(),
                budget.getStartDate(), budget.getEndDate());
        return buildResponse(budget, spent);
    }

    public List<BudgetResponse> getUserBudgets(Long userId) {
        List<Budget> budgets = budgetRepository.findByUserId(userId);
        return mapBudgetsToResponses(userId, budgets);
    }

    public List<BudgetResponse> getActiveBudgets(Long userId) {
        List<Budget> budgets = budgetRepository.findActiveBudgetsByUserIdAndDate(userId, LocalDate.now());
        return mapBudgetsToResponses(userId, budgets);
    }

    public BudgetSummaryResponse getBudgetSummary(Long userId) {
        LocalDate today = LocalDate.now();
        List<Budget> activeBudgets = budgetRepository.findActiveBudgetsByUserIdAndDate(userId, today);

        if (activeBudgets.isEmpty()) {
            return BudgetSummaryResponse.builder()
                    .totalBudget(BigDecimal.ZERO)
                    .totalSpent(BigDecimal.ZERO)
                    .totalRemaining(BigDecimal.ZERO)
                    .overallPercentageUsed(0.0)
                    .categoriesOverBudget(0)
                    .budgets(Collections.emptyList())
                    .build();
        }

        // ── Single bulk query instead of N individual SUM queries ──────────────
        // Budgets may span different date ranges (monthly vs custom), so we need
        // per-budget date windows. We group budgets by their date range, fire one
        // query per unique window, and merge results into a single spendMap.
        //
        // In practice almost all active budgets share the same period window
        // (e.g. current month), so this typically reduces to 1-2 DB queries
        // regardless of how many budget categories the user has.
        Map<Long, BigDecimal> spendMap = buildSpendMap(userId, activeBudgets);

        // ── Build responses using the pre-fetched map (zero DB hits in the loop) ─
        List<BudgetResponse> budgetResponses = activeBudgets.stream()
                .map(b -> buildResponse(b, spendMap.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        // ── Aggregate totals ───────────────────────────────────────────────────
        BigDecimal totalBudget = budgetResponses.stream()
                .map(BudgetResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpent = budgetResponses.stream()
                .map(BudgetResponse::getSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long categoriesOverBudget = budgetResponses.stream()
                .filter(BudgetResponse::getIsOverBudget)
                .count();

        BigDecimal totalRemaining = totalBudget.subtract(totalSpent);
        Double overallPercentage = totalBudget.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.divide(totalBudget, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        return BudgetSummaryResponse.builder()
                .totalBudget(totalBudget)
                .totalSpent(totalSpent)
                .totalRemaining(totalRemaining)
                .overallPercentageUsed(overallPercentage)
                .categoriesOverBudget((int) categoriesOverBudget)
                .budgets(budgetResponses)
                .build();
    }

    public BudgetResponse getBudgetById(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));

        if (!budget.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to budget");
        }

        BigDecimal spent = fetchSpent(userId, budget.getCategory().getId(),
                budget.getStartDate(), budget.getEndDate());
        return buildResponse(budget, spent);
    }

    @Transactional
    public BudgetResponse updateBudget(Long userId, Long budgetId, BudgetRequest request) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));

        if (!budget.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to budget");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }

        budget.setCategory(category);
        budget.setAmount(request.getAmount());
        budget.setPeriod(request.getPeriod());
        budget.setStartDate(request.getStartDate());
        budget.setEndDate(request.getEndDate());

        budget = budgetRepository.save(budget);
        log.info("Updated budget {} for user {}", budgetId, userId);

        BigDecimal spent = fetchSpent(userId, budget.getCategory().getId(),
                budget.getStartDate(), budget.getEndDate());
        return buildResponse(budget, spent);
    }

    @Transactional
    public void deleteBudget(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));

        if (!budget.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to budget");
        }

        budgetRepository.delete(budget);
        log.info("Deleted budget {} for user {}", budgetId, userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Maps a list of budgets to responses using a single bulk spend query.
     * Replaces the old pattern of calling mapToResponse() in a stream,
     * which fired one DB query per budget.
     */
    private List<BudgetResponse> mapBudgetsToResponses(Long userId, List<Budget> budgets) {
        if (budgets.isEmpty()) return Collections.emptyList();
        Map<Long, BigDecimal> spendMap = buildSpendMap(userId, budgets);
        return budgets.stream()
                .map(b -> buildResponse(b, spendMap.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    /**
     * Builds a categoryId → totalSpent map by grouping budgets by their date
     * range and firing one bulk query per unique window.
     *
     * Most users have all budgets on the same period (e.g. current month),
     * so this is typically just 1 DB query regardless of budget count.
     */
    private Map<Long, BigDecimal> buildSpendMap(Long userId, List<Budget> budgets) {
        // Group budgets by their date window (start+end pair)
        record DateWindow(LocalDate start, LocalDate end) {}

        Map<DateWindow, List<Budget>> byWindow = budgets.stream()
                .collect(Collectors.groupingBy(b -> new DateWindow(b.getStartDate(), b.getEndDate())));

        // Fire one bulk query per unique date window, merge into a single map
        return byWindow.entrySet().stream()
                .flatMap(entry -> {
                    DateWindow window = entry.getKey();
                    List<Long> categoryIds = entry.getValue().stream()
                            .map(b -> b.getCategory().getId())
                            .collect(Collectors.toList());

                    List<Object[]> rows = transactionRepository.sumAmountGroupedByCategoryIds(
                            userId, categoryIds, window.start(), window.end());

                    return rows.stream();
                })
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (BigDecimal) row[1],
                        BigDecimal::add // merge if same category appears in multiple windows
                ));
    }

    /**
     * Single-category spend fetch — used for individual budget operations
     * (create, get by id, update) where a loop is not involved.
     */
    private BigDecimal fetchSpent(Long userId, Long categoryId,
                                  LocalDate startDate, LocalDate endDate) {
        BigDecimal spent = transactionRepository.sumAmountByUserIdAndCategoryIdAndDateRange(
                userId, categoryId, startDate, endDate);
        return spent != null ? spent : BigDecimal.ZERO;
    }

    /**
     * Builds a BudgetResponse from a Budget and a pre-computed spent amount.
     * No DB calls — all data is passed in.
     */
    private BudgetResponse buildResponse(Budget budget, BigDecimal spent) {
        BigDecimal remaining = budget.getAmount().subtract(spent);
        Double percentageUsed = budget.getAmount().compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(budget.getAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        return BudgetResponse.builder()
                .id(budget.getId())
                .categoryId(budget.getCategory().getId())
                .categoryName(budget.getCategory().getName())
                .amount(budget.getAmount())
                .spent(spent)
                .remaining(remaining)
                .percentageUsed(percentageUsed)
                .period(budget.getPeriod())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .isOverBudget(spent.compareTo(budget.getAmount()) > 0)
                .build();
    }
}