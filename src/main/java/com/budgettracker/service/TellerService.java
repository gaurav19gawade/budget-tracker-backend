package com.budgettracker.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Transactional
    public TellerAccountResponse connectEnrollment(Long userId, String accessToken,
                                                   String enrollmentId, String institutionName) {
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
        categorizationService.categorizeForUser(enrollment.getUser().getId());
        log.info("Auto-categorization complete for user {}", enrollment.getUser().getId());

        syncTransactions(enrollment.getId());

        return TellerAccountResponse.builder()
                .id(enrollment.getId())
                .institutionName(enrollment.getInstitutionName())
                .connectedAt(enrollment.getCreatedAt())
                .environment(enrollment.getEnvironment())
                .lastSyncedAt(enrollment.getLastSyncedAt())
                .build();
    }

    @Transactional
    public void syncTransactions(Long tellerEnrollmentDbId) {
        TellerEnrollment enrollment = tellerEnrollmentRepository.findById(tellerEnrollmentDbId)
                .orElseThrow(() -> new RuntimeException("Teller enrollment not found"));

        List<Map<String, Object>> accounts = listAccounts(enrollment.getAccessToken());

        LocalDate endDate   = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        // ── Step 1: fetch ALL existing Teller IDs for this enrollment in one query ──
        // Previously: existsByTellerTransactionId() was called once per transaction
        // in the inner loop — 1 SELECT per row. Now it's 1 SELECT total.
        Set<String> existingIds = transactionRepository
                .findExistingTellerTransactionIds(tellerEnrollmentDbId);

        log.debug("Enrollment {} already has {} synced transactions",
                tellerEnrollmentDbId, existingIds.size());

        // ── Step 2: build list of new transactions across all accounts ───────────
        List<Transaction> toSave = new ArrayList<>();

        for (Map<String, Object> account : accounts) {
            String accountId = Objects.toString(account.get("id"), null);
            if (accountId == null) continue;

            String accountType     = Objects.toString(account.get("type"), null);
            String accountSubtype  = Objects.toString(account.get("subtype"), null);
            String accountName     = Objects.toString(account.get("name"), "Unknown Account");
            String accountLastFour = Objects.toString(account.get("last_four"), null);

            List<Map<String, Object>> txns = listTransactions(enrollment.getAccessToken(), accountId);

            for (Map<String, Object> t : txns) {
                String tellerTxnId = Objects.toString(t.get("id"), null);
                if (tellerTxnId == null) continue;

                // ── Duplicate check is now an O(1) Set lookup, not a DB query ──
                if (existingIds.contains(tellerTxnId)) continue;

                BigDecimal amount = parseAmount(t.get("amount"));
                LocalDate date    = parseDate(t.get("date"));
                if (date == null || date.isBefore(startDate) || date.isAfter(endDate)) continue;

                String description = Objects.toString(t.get("description"), "");
                String merchant    = Objects.toString(t.get("merchant_name"), null);
                if (merchant == null || merchant.isBlank()) {
                    merchant = Objects.toString(t.get("counterparty"), description);
                }

                boolean pending = parseBoolean(t.get("pending"));

                toSave.add(Transaction.builder()
                        .user(enrollment.getUser())
                        .tellerEnrollment(enrollment)
                        .tellerTransactionId(tellerTxnId)
                        .tellerAccountId(accountId)
                        .accountType(accountType)
                        .accountSubtype(accountSubtype)
                        .accountName(accountName)
                        .accountLastFour(accountLastFour)
                        .amount(amount != null ? amount : BigDecimal.ZERO)
                        .date(date)
                        .merchantName(merchant)
                        .description(description)
                        .isManual(false)
                        .pending(pending)
                        .build());
            }
        }

        // ── Step 3: single bulk insert instead of N individual INSERTs ───────────
        // saveAll() combined with spring.jpa.properties.hibernate.jdbc.batch_size
        // in application.properties lets Hibernate send all inserts in batched
        // JDBC statements rather than one round trip per row.
        if (!toSave.isEmpty()) {
            transactionRepository.saveAll(toSave);
        }

        enrollment.setLastSyncedAt(LocalDateTime.now());
        tellerEnrollmentRepository.save(enrollment);

        log.info("Synced Teller enrollment {} — accounts: {}, new txns saved: {}",
                tellerEnrollmentDbId, accounts.size(), toSave.size());
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

        tellerEnrollmentRepository.delete(enrollment);
        log.info("Removed Teller enrollment {} for user {}", tellerEnrollmentDbId, userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Map<String, Object>> listAccounts(String accessToken) {
        String url = baseUrl + "/accounts";
        HttpEntity<Void> entity = new HttpEntity<>(basicAuthHeaders(accessToken));
        ResponseEntity<List> response = tellerRestTemplate.exchange(url, HttpMethod.GET, entity, List.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to list Teller accounts");
        }
        return (List<Map<String, Object>>) (List<?>) response.getBody();
    }

    private List<Map<String, Object>> listTransactions(String accessToken, String accountId) {
        String url = baseUrl + "/accounts/" + accountId + "/transactions";
        HttpEntity<Void> entity = new HttpEntity<>(basicAuthHeaders(accessToken));
        ResponseEntity<List> response = tellerRestTemplate.exchange(url, HttpMethod.GET, entity, List.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to list Teller transactions for account " + accountId);
        }
        return (List<Map<String, Object>>) (List<?>) response.getBody();
    }

    private HttpHeaders basicAuthHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(accessToken, "");
        return headers;
    }

    private BigDecimal parseAmount(Object amountObj) {
        if (amountObj == null) return null;
        try { return new BigDecimal(amountObj.toString()); }
        catch (Exception e) { return null; }
    }

    private LocalDate parseDate(Object dateObj) {
        if (dateObj == null) return null;
        try { return LocalDate.parse(dateObj.toString()); }
        catch (Exception e) { return null; }
    }

    private boolean parseBoolean(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}