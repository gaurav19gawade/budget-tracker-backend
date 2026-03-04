package com.budgettracker.service;

import com.budgettracker.dto.BudgetRequest;
import com.budgettracker.dto.BudgetResponse;
import com.budgettracker.dto.BudgetSummaryResponse;
import com.budgettracker.exception.BadRequestException;
import com.budgettracker.exception.ResourceNotFoundException;
import com.budgettracker.model.Budget;
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
import java.util.List;
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

        // Verify category belongs to user
        if (!category.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        // Validate dates
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }

        // Check for overlapping budgets
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

        return mapToResponse(budget);
    }

    public List<BudgetResponse> getUserBudgets(Long userId) {
        return budgetRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<BudgetResponse> getActiveBudgets(Long userId) {
        LocalDate today = LocalDate.now();
        return budgetRepository.findActiveBudgetsByUserIdAndDate(userId, today).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public BudgetSummaryResponse getBudgetSummary(Long userId) {
        LocalDate today = LocalDate.now();
        List<Budget> activeBudgets = budgetRepository.findActiveBudgetsByUserIdAndDate(userId, today);

        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;
        int categoriesOverBudget = 0;

        List<BudgetResponse> budgetResponses = activeBudgets.stream()
                .map(budget -> {
                    BudgetResponse response = mapToResponse(budget);
                    return response;
                })
                .collect(Collectors.toList());

        for (BudgetResponse response : budgetResponses) {
            totalBudget = totalBudget.add(response.getAmount());
            totalSpent = totalSpent.add(response.getSpent());
            if (response.getIsOverBudget()) {
                categoriesOverBudget++;
            }
        }

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
                .categoriesOverBudget(categoriesOverBudget)
                .budgets(budgetResponses)
                .build();
    }

    public BudgetResponse getBudgetById(Long userId, Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));

        if (!budget.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to budget");
        }

        return mapToResponse(budget);
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

        // Validate dates
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

        return mapToResponse(budget);
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

    private BudgetResponse mapToResponse(Budget budget) {
        // Calculate spent amount
        BigDecimal spent = transactionRepository.sumAmountByUserIdAndCategoryIdAndDateRange(
                budget.getUser().getId(),
                budget.getCategory().getId(),
                budget.getStartDate(),
                budget.getEndDate()
        );

        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

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