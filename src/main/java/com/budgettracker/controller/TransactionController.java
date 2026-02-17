package com.budgettracker.controller;

import com.budgettracker.dto.TransactionRequest;
import com.budgettracker.dto.TransactionResponse;
import com.budgettracker.dto.TransactionUpdateRequest;
import com.budgettracker.security.UserPrincipal;
import com.budgettracker.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TransactionRequest request) {

        TransactionResponse response = transactionService.createTransaction(currentUser.getId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getUserTransactions(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String accountSubtype) {

        // Filter by account type/subtype
        if (accountType != null || accountSubtype != null) {
            List<TransactionResponse> transactions = transactionService.getTransactionsByAccountType(
                    currentUser.getId(), accountType, accountSubtype);
            return ResponseEntity.ok(transactions);
        }

        // Filter by category
        if (categoryId != null) {
            List<TransactionResponse> transactions = transactionService.getTransactionsByCategory(
                    currentUser.getId(), categoryId);
            return ResponseEntity.ok(transactions);
        }

        // Filter by date range
        if (startDate != null && endDate != null) {
            List<TransactionResponse> transactions = transactionService.getTransactionsByDateRange(
                    currentUser.getId(), startDate, endDate);
            return ResponseEntity.ok(transactions);
        }

        // Return all transactions
        List<TransactionResponse> transactions = transactionService.getUserTransactions(currentUser.getId());
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {

        TransactionResponse transaction = transactionService.getTransactionById(currentUser.getId(), id);
        return ResponseEntity.ok(transaction);
    }

    @PutMapping("/{id}/category")
    public ResponseEntity<TransactionResponse> updateTransactionCategory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @Valid @RequestBody TransactionUpdateRequest request) {

        TransactionResponse response = transactionService.updateTransactionCategory(
                currentUser.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {

        transactionService.deleteTransaction(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}