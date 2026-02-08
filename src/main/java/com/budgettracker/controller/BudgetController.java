package com.budgettracker.controller;

import com.budgettracker.dto.BudgetRequest;
import com.budgettracker.dto.BudgetResponse;
import com.budgettracker.dto.BudgetSummaryResponse;
import com.budgettracker.security.UserPrincipal;
import com.budgettracker.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody BudgetRequest request) {

        BudgetResponse response = budgetService.createBudget(currentUser.getId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> getUserBudgets(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<BudgetResponse> budgets = budgetService.getUserBudgets(currentUser.getId());
        return ResponseEntity.ok(budgets);
    }

    @GetMapping("/active")
    public ResponseEntity<List<BudgetResponse>> getActiveBudgets(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<BudgetResponse> budgets = budgetService.getActiveBudgets(currentUser.getId());
        return ResponseEntity.ok(budgets);
    }

    @GetMapping("/summary")
    public ResponseEntity<BudgetSummaryResponse> getBudgetSummary(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        BudgetSummaryResponse summary = budgetService.getBudgetSummary(currentUser.getId());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponse> getBudgetById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {

        BudgetResponse budget = budgetService.getBudgetById(currentUser.getId(), id);
        return ResponseEntity.ok(budget);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponse> updateBudget(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @Valid @RequestBody BudgetRequest request) {

        BudgetResponse response = budgetService.updateBudget(currentUser.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {

        budgetService.deleteBudget(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}