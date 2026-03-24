package com.budgettracker.service;

import com.budgettracker.dto.SyncResult;
import com.budgettracker.dto.TellerAccountResponse;
import com.budgettracker.model.TellerEnrollment;
import com.budgettracker.model.Transaction;
import com.budgettracker.model.User;
import com.budgettracker.repository.TellerEnrollmentRepository;
import com.budgettracker.repository.TransactionRepository;
import com.budgettracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TellerService {

    private final RestTemplate tellerRestTemplate;
    private final TellerEnrollmentRepository tellerEnrollmentRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategorizationService categorizationService;

    @Value("${teller.base-url}")
    private String baseUrl;

    @Value("${teller.connect.env:sandbox}")
    private String tellerEnvironment;

    /**
     * Teller Connect returns accessToken + enrollment info directly (no Plaid-style exchange). :contentReference[oaicite:6]{index=6}
     */
    @Transactional
    public TellerAccountResponse connectEnrollment(Long userId, String accessToken, String enrollmentId, String institutionName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TellerEnrollment enrollment = TellerEnrollment.builder()
                .user(user)
                .accessToken(accessToken)
                .enrollmentId(enrollmentId)
                .institutionName(institutionName != null ? institutionName : "Unknown Institution")
                .environment(tellerEnvironment)
                .build();

        enrollment = tellerEnrollmentRepository.save(enrollment);

        // Sync immediately — categorization (keyword + LLM) runs inside syncTransactions
        syncTransactions(enrollment.getId());

        return TellerAccountResponse.builder()
                .id(enrollment.getId())
                .institutionName(enrollment.getInstitutionName())
                .connectedAt(enrollment.getCreatedAt())
                .environment(enrollment.getEnvironment())
                .lastSyncedAt(enrollment.getLastSyncedAt())
                .build();
    }

    /**
     * Sync transactions for all accounts under this enrollment.
     * Teller returns accounts list for the access token, then transactions per account. :contentReference[oaicite:7]{index=7}
     */
    // Note: NOT @Transactional — saveAll() commits atomically per enrollment, which is fine
    // because removeStaleEnrollments() ensures at most one enrollment per user+institution.
    public SyncResult syncTransactions(Long tellerEnrollmentDbId) {
        TellerEnrollment enrollment = tellerEnrollmentRepository.findById(tellerEnrollmentDbId)
                .orElseThrow(() -> new RuntimeException("Teller enrollment not found"));

        List<Map<String, Object>> accounts = listAccounts(enrollment.getAccessToken());

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        // Pre-load all teller IDs already stored for this enrollment in one query.
        // Avoids an existsByTellerTransactionId() round-trip for every row in the loop.
        Set<String> knownIds = transactionRepository.findExistingTellerTransactionIds(tellerEnrollmentDbId);

        List<Transaction> toInsert = new ArrayList<>();

        for (Map<String, Object> account : accounts) {
            String accountId = Objects.toString(account.get("id"), null);
            if (accountId == null) continue;

            String accountType = Objects.toString(account.get("type"), null);
            String accountSubtype = Objects.toString(account.get("subtype"), null);
            String accountName = Objects.toString(account.get("name"), "Unknown Account");
            String accountLastFour = Objects.toString(account.get("last_four"), null);

            List<Map<String, Object>> txns;
            try {
                txns = listTransactions(enrollment.getAccessToken(), accountId);
            } catch (HttpClientErrorException e) {
                // 410 Gone = account closed at the bank; 403 = access revoked.
                // Log and skip this account — don’t fail the whole enrollment sync.
                log.warn("Skipping account {} ({}): {} — {}",
                        accountId, accountName, e.getStatusCode(), e.getMessage());
                continue;
            } catch (Exception e) {
                log.warn("Unexpected error fetching transactions for account {} ({}): {}",
                        accountId, accountName, e.getMessage());
                continue;
            }

            for (Map<String, Object> t : txns) {
                String tellerTxnId = Objects.toString(t.get("id"), null);
                if (tellerTxnId == null) continue;

                // Derive direction from amount sign — this is the ground truth for credit vs debit.
                // Teller’s "type" field encodes payment method (ach, wire, check, card_payment, etc.),
                // NOT direction. A positive amount means money arrived (credit); negative means money left (debit).
                BigDecimal rawAmount = parseAmount(t.get("amount"));
                String transactionType = (rawAmount != null && rawAmount.compareTo(BigDecimal.ZERO) > 0)
                        ? "credit" : "debit";

                if (knownIds.contains(tellerTxnId)) {
                    // Row already exists. Correct the stored type if it diverges from Teller’s value —
                    // this repairs rows that were mis-classified by the old payment-method mapping.
                    transactionRepository.findByTellerTransactionId(tellerTxnId)
                            .filter(existing -> !transactionType.equals(existing.getTransactionType()))
                            .ifPresent(existing -> {
                                existing.setTransactionType(transactionType);
                                transactionRepository.save(existing);
                                log.debug("Corrected transactionType for {} → {}", tellerTxnId, transactionType);
                            });
                    continue;
                }

                LocalDate date = parseDate(t.get("date"));
                if (date == null || date.isBefore(startDate) || date.isAfter(endDate)) continue;

                String description = Objects.toString(t.get("description"), "");
                String merchant = Objects.toString(t.get("merchant_name"), null);
                if (merchant == null || merchant.isBlank()) {
                    merchant = Objects.toString(t.get("counterparty"), description);
                }

                boolean pending = parseBoolean(t.get("pending"));

                toInsert.add(Transaction.builder()
                        .user(enrollment.getUser())
                        .tellerEnrollment(enrollment)
                        .tellerTransactionId(tellerTxnId)
                        .tellerAccountId(accountId)
                        .accountType(accountType)
                        .accountSubtype(accountSubtype)
                        .accountName(accountName)
                        .accountLastFour(accountLastFour)
                        .amount(rawAmount != null ? rawAmount.abs() : BigDecimal.ZERO)
                        .date(date)
                        .merchantName(merchant)
                        .description(description)
                        .transactionType(transactionType)
                        .isManual(false)
                        .pending(pending)
                        .build());
            }
        }

        // Batch insert — one round-trip instead of one per transaction.
        if (!toInsert.isEmpty()) {
            transactionRepository.saveAll(toInsert);
        }
        int saved = toInsert.size();

        enrollment.setLastSyncedAt(java.time.LocalDateTime.now());
        tellerEnrollmentRepository.save(enrollment);

        // Run categorization after every sync — keyword pass first, LLM fallback for remainder.
        // Only touches transactions with category == null (manual assignments preserved).
        int categorized = 0;
        if (saved > 0) {
            categorized = categorizationService.categorizeForUser(enrollment.getUser().getId());
            log.info("Auto-categorized {} transactions for enrollment {}", categorized, tellerEnrollmentDbId);
        }

        log.info("Synced Teller enrollment {} - accounts: {}, new txns saved: {}",
                tellerEnrollmentDbId, accounts.size(), saved);

        return SyncResult.builder()
                .accountsFound(accounts.size())
                .transactionsSynced(saved)
                .transactionsCategorized(categorized)
                .build();
    }

    public List<TellerAccountResponse> getConnectedAccounts(Long userId) {
        return tellerEnrollmentRepository.findByUserId(userId).stream()
                .map(e -> TellerAccountResponse.builder()
                        .id(e.getId())
                        .institutionName(e.getInstitutionName())
                        .connectedAt(e.getCreatedAt())
                        .lastSyncedAt(e.getLastSyncedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeAccount(Long tellerEnrollmentDbId, Long userId) {
        TellerEnrollment enrollment = tellerEnrollmentRepository.findById(tellerEnrollmentDbId)
                .orElseThrow(() -> new RuntimeException("Teller enrollment not found"));

        if (!enrollment.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        // Teller does not have a direct analog of Plaid ItemRemove in the same way;
        // you typically revoke/rotate certs / deauthorize via dashboard workflows.
        // We delete local record.
        tellerEnrollmentRepository.delete(enrollment);
        log.info("Removed Teller enrollment {} for user {}", tellerEnrollmentDbId, userId);
    }

    private List<Map<String, Object>> listAccounts(String accessToken) {
        String url = baseUrl + "/accounts";

        HttpHeaders headers = basicAuthHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List> response = tellerRestTemplate.exchange(url, HttpMethod.GET, entity, List.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to list Teller accounts");
        }

        // each element is a map-like object (Jackson)
        return (List<Map<String, Object>>) (List<?>) response.getBody();
    }

    private List<Map<String, Object>> listTransactions(String accessToken, String accountId) {
        // Teller transactions endpoint is per account. :contentReference[oaicite:8]{index=8}
        String url = baseUrl + "/accounts/" + accountId + "/transactions";

        HttpHeaders headers = basicAuthHeaders(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List> response = tellerRestTemplate.exchange(url, HttpMethod.GET, entity, List.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to list Teller transactions for account " + accountId);
        }

        return (List<Map<String, Object>>) (List<?>) response.getBody();
    }

    private HttpHeaders basicAuthHeaders(String accessToken) {
        // Teller uses HTTP Basic Auth; username=accessToken, password is blank. :contentReference[oaicite:9]{index=9}
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(accessToken, "");
        return headers;
    }

    private BigDecimal parseAmount(Object amountObj) {
        if (amountObj == null) return null;
        try {
            return new BigDecimal(amountObj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(Object dateObj) {
        if (dateObj == null) return null;
        try {
            return LocalDate.parse(dateObj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean parseBoolean(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}