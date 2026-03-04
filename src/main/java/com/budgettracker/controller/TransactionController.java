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

        // Previously: a chain of if/else that applied only ONE filter even when
        // multiple were passed — accountType won over categoryId, categoryId won
        // over date range, etc. Filters were mutually exclusive by accident.
        //
        // Now: all filters are passed to the service together. The service and
        // repository handle nullable params gracefully — null means "no filter
        // on this field", so any combination works correctly.
        List<TransactionResponse> transactions = transactionService.getTransactions(
                currentUser.getId(), startDate, endDate, categoryId, accountType, accountSubtype);

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