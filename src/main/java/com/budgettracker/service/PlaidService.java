package com.budgettracker.service;

import com.budgettracker.dto.PlaidAccountResponse;
import com.budgettracker.dto.PlaidLinkTokenResponse;
import com.budgettracker.model.PlaidItem;
import com.budgettracker.model.Transaction;
import com.budgettracker.model.User;
import com.budgettracker.repository.PlaidItemRepository;
import com.budgettracker.repository.TransactionRepository;
import com.budgettracker.repository.UserRepository;
import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaidService {

    private final PlaidApi plaidClient;
    private final PlaidItemRepository plaidItemRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.redirect-uri}")
    private String redirectUri;

    /**
     * Create a Link token for Plaid Link initialization
     */
    public PlaidLinkTokenResponse createLinkToken(Long userId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LinkTokenCreateRequestUser linkUser = new LinkTokenCreateRequestUser()
                .clientUserId(user.getId().toString());

        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(linkUser)
                .clientName("Budget Tracker")
                .products(Arrays.asList(Products.TRANSACTIONS))
                .countryCodes(Arrays.asList(CountryCode.US))
                .language("en")
                .redirectUri(redirectUri);

        try {
            retrofit2.Response<LinkTokenCreateResponse> response =
                    plaidClient.linkTokenCreate(request).execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                log.error("Failed to create link token: {}", errorBody);
                throw new RuntimeException("Failed to create Plaid link token: " + errorBody);
            }

            LinkTokenCreateResponse linkToken = response.body();

            return PlaidLinkTokenResponse.builder()
                    .linkToken(linkToken.getLinkToken())
                    .expiration(linkToken.getExpiration().toString())
                    .build();
        } catch (IOException e) {
            log.error("IOException while creating link token", e);
            throw e;
        }
    }

    /**
     * Exchange public token for access token
     */
    @Transactional
    public PlaidAccountResponse exchangePublicToken(Long userId, String publicToken, String institutionName) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ItemPublicTokenExchangeRequest request =
                new ItemPublicTokenExchangeRequest()
                        .publicToken(publicToken);

        try {
            retrofit2.Response<ItemPublicTokenExchangeResponse> response =
                    plaidClient.itemPublicTokenExchange(request).execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                log.error("Failed to exchange public token: {}", errorBody);
                throw new RuntimeException("Failed to exchange public token: " + errorBody);
            }

            ItemPublicTokenExchangeResponse exchangeResponse = response.body();

            // Save Plaid item
            PlaidItem plaidItem = PlaidItem.builder()
                    .user(user)
                    .plaidItemId(exchangeResponse.getItemId())
                    .plaidAccessToken(exchangeResponse.getAccessToken())
                    .institutionName(institutionName != null ? institutionName : "Unknown Institution")
                    .build();

            plaidItem = plaidItemRepository.save(plaidItem);

            // Sync transactions immediately
            syncTransactions(plaidItem.getId());

            return PlaidAccountResponse.builder()
                    .id(plaidItem.getId())
                    .institutionName(plaidItem.getInstitutionName())
                    .connectedAt(plaidItem.getCreatedAt())
                    .lastSyncedAt(plaidItem.getLastSyncedAt())
                    .build();
        } catch (IOException e) {
            log.error("IOException while exchanging public token", e);
            throw e;
        }
    }

    /**
     * Sync transactions for a Plaid item
     */
    @Transactional
    public void syncTransactions(Long plaidItemId) throws IOException {
        PlaidItem plaidItem = plaidItemRepository.findById(plaidItemId)
                .orElseThrow(() -> new RuntimeException("Plaid item not found"));

        // Get transactions from last 30 days
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        TransactionsGetRequest request = new TransactionsGetRequest()
                .accessToken(plaidItem.getPlaidAccessToken())
                .startDate(startDate)
                .endDate(endDate);

        try {
            retrofit2.Response<TransactionsGetResponse> response =
                    plaidClient.transactionsGet(request).execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                log.error("Failed to get transactions: {}", errorBody);
                throw new RuntimeException("Failed to sync transactions: " + errorBody);
            }

            TransactionsGetResponse transactionsResponse = response.body();
            List<com.plaid.client.model.Transaction> plaidTransactions =
                    transactionsResponse.getTransactions();

            log.info("Syncing {} transactions for item {}", plaidTransactions.size(), plaidItemId);

            // Save transactions
            for (com.plaid.client.model.Transaction plaidTxn : plaidTransactions) {
                // Skip if already exists
                if (transactionRepository.existsByPlaidTransactionId(plaidTxn.getTransactionId())) {
                    continue;
                }

                Transaction transaction = Transaction.builder()
                        .user(plaidItem.getUser())
                        .plaidItem(plaidItem)
                        .plaidTransactionId(plaidTxn.getTransactionId())
                        .amount(BigDecimal.valueOf(plaidTxn.getAmount()))
                        .date(plaidTxn.getDate())
                        .merchantName(plaidTxn.getMerchantName() != null ?
                                plaidTxn.getMerchantName() : plaidTxn.getName())
                        .description(plaidTxn.getName())
                        .isManual(false)
                        .pending(plaidTxn.getPending())
                        .build();

                transactionRepository.save(transaction);
            }

            // Update last synced time
            plaidItem.setLastSyncedAt(java.time.LocalDateTime.now());
            plaidItemRepository.save(plaidItem);

            log.info("Successfully synced transactions for item {}", plaidItemId);
        } catch (IOException e) {
            log.error("IOException while syncing transactions", e);
            throw e;
        }
    }

    /**
     * Get all connected accounts for a user
     */
    public List<PlaidAccountResponse> getConnectedAccounts(Long userId) {
        List<PlaidItem> plaidItems = plaidItemRepository.findByUserId(userId);

        return plaidItems.stream()
                .map(item -> PlaidAccountResponse.builder()
                        .id(item.getId())
                        .institutionName(item.getInstitutionName())
                        .connectedAt(item.getCreatedAt())
                        .lastSyncedAt(item.getLastSyncedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Remove a connected account
     */
    @Transactional
    public void removeAccount(Long plaidItemId, Long userId) throws IOException {
        PlaidItem plaidItem = plaidItemRepository.findById(plaidItemId)
                .orElseThrow(() -> new RuntimeException("Plaid item not found"));

        // Verify ownership
        if (!plaidItem.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        // Remove item from Plaid
        ItemRemoveRequest request = new ItemRemoveRequest()
                .accessToken(plaidItem.getPlaidAccessToken());

        try {
            plaidClient.itemRemove(request).execute();
        } catch (IOException e) {
            log.error("Failed to remove item from Plaid", e);
            // Continue with local deletion even if Plaid API fails
        }

        // Delete from database
        plaidItemRepository.delete(plaidItem);

        log.info("Removed Plaid item {} for user {}", plaidItemId, userId);
    }
}