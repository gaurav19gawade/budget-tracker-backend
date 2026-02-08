package com.budgettracker.controller;

import com.budgettracker.dto.PlaidAccountResponse;
import com.budgettracker.dto.PlaidLinkTokenResponse;
import com.budgettracker.dto.PlaidPublicTokenRequest;
import com.budgettracker.security.UserPrincipal;
import com.budgettracker.service.PlaidService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/plaid")
@RequiredArgsConstructor
@Slf4j
public class PlaidController {

    private final PlaidService plaidService;

    @PostMapping("/create-link-token")
    public ResponseEntity<PlaidLinkTokenResponse> createLinkToken(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            PlaidLinkTokenResponse response = plaidService.createLinkToken(currentUser.getId());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error creating Plaid link token", e);
            throw new RuntimeException("Failed to create Plaid link token");
        }
    }

    @PostMapping("/exchange-public-token")
    public ResponseEntity<PlaidAccountResponse> exchangePublicToken(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody PlaidPublicTokenRequest request) {

        try {
            PlaidAccountResponse response = plaidService.exchangePublicToken(
                    currentUser.getId(),
                    request.getPublicToken(),
                    request.getInstitutionName());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error exchanging Plaid public token", e);
            throw new RuntimeException("Failed to connect bank account");
        }
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<PlaidAccountResponse>> getConnectedAccounts(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<PlaidAccountResponse> accounts = plaidService.getConnectedAccounts(currentUser.getId());
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/sync/{itemId}")
    public ResponseEntity<Void> syncTransactions(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long itemId) {

        try {
            plaidService.syncTransactions(itemId);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Error syncing transactions", e);
            throw new RuntimeException("Failed to sync transactions");
        }
    }

    @DeleteMapping("/accounts/{itemId}")
    public ResponseEntity<Void> removeAccount(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long itemId) {

        try {
            plaidService.removeAccount(itemId, currentUser.getId());
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            log.error("Error removing Plaid account", e);
            throw new RuntimeException("Failed to remove bank account");
        }
    }
}