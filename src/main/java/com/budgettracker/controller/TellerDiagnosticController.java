package com.budgettracker.controller;

import com.budgettracker.model.TellerEnrollment;
import com.budgettracker.repository.TellerEnrollmentRepository;
import com.budgettracker.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Diagnostic endpoint — lets you inspect the real-time status of your
 * connected Teller accounts directly from Teller's API.
 * Useful since Teller has no dashboard (app.teller.io doesn't exist).
 */
@RestController
@RequestMapping("/api/teller/diagnostic")
@RequiredArgsConstructor
@Slf4j
public class TellerDiagnosticController {

    @Qualifier("tellerRestTemplate")
    private final RestTemplate tellerRestTemplate;

    private final TellerEnrollmentRepository tellerEnrollmentRepository;

    @Value("${teller.base-url}")
    private String baseUrl;

    /**
     * Returns the status of all Teller accounts for the current user,
     * fetched live from Teller's API. Shows account name, type, status,
     * and whether transactions are accessible.
     *
     * GET /api/teller/diagnostic/accounts
     */
    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> diagnose(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<TellerEnrollment> enrollments =
                tellerEnrollmentRepository.findByUserId(currentUser.getId());

        if (enrollments.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "enrollments", 0,
                    "message", "No Teller enrollments found for this user.",
                    "accounts", List.of()
            ));
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (TellerEnrollment enrollment : enrollments) {
            Map<String, Object> enrollmentResult = new LinkedHashMap<>();
            enrollmentResult.put("enrollmentId",    enrollment.getId());
            enrollmentResult.put("institution",     enrollment.getInstitutionName());
            enrollmentResult.put("environment",     enrollment.getEnvironment());
            enrollmentResult.put("connectedAt",     enrollment.getCreatedAt());
            enrollmentResult.put("lastSyncedAt",    enrollment.getLastSyncedAt());

            // Fetch accounts from Teller
            List<Map<String, Object>> tellerAccounts;
            try {
                tellerAccounts = listAccounts(enrollment.getAccessToken());
                enrollmentResult.put("tellerApiStatus", "OK");
            } catch (HttpClientErrorException e) {
                enrollmentResult.put("tellerApiStatus", e.getStatusCode().toString());
                enrollmentResult.put("tellerApiError",  e.getResponseBodyAsString());
                enrollmentResult.put("accounts", List.of());
                results.add(enrollmentResult);
                continue;
            } catch (Exception e) {
                enrollmentResult.put("tellerApiStatus", "ERROR");
                enrollmentResult.put("tellerApiError",  e.getMessage());
                enrollmentResult.put("accounts", List.of());
                results.add(enrollmentResult);
                continue;
            }

            // For each account, check transaction accessibility
            List<Map<String, Object>> accountDetails = new ArrayList<>();
            for (Map<String, Object> acct : tellerAccounts) {
                Map<String, Object> detail = new LinkedHashMap<>();
                String accountId = Objects.toString(acct.get("id"), null);
                detail.put("accountId",   accountId);
                detail.put("name",        acct.get("name"));
                detail.put("type",        acct.get("type"));
                detail.put("subtype",     acct.get("subtype"));
                detail.put("status",      acct.get("status"));      // "open" or "closed"
                detail.put("lastFour",    acct.get("last_four"));
                detail.put("institution", acct.get("institution"));

                // Try fetching transactions to check if accessible
                if (accountId != null) {
                    try {
                        List<?> txns = listTransactions(enrollment.getAccessToken(), accountId);
                        detail.put("transactionAccess", "OK");
                        detail.put("transactionCount",  txns.size());
                    } catch (HttpClientErrorException e) {
                        detail.put("transactionAccess", e.getStatusCode().toString());
                        detail.put("transactionError",  parseErrorMessage(e.getResponseBodyAsString()));
                    } catch (Exception e) {
                        detail.put("transactionAccess", "ERROR");
                        detail.put("transactionError",  e.getMessage());
                    }
                }

                accountDetails.add(detail);
            }

            enrollmentResult.put("accounts", accountDetails);
            results.add(enrollmentResult);
        }

        // Summary
        long totalAccounts   = results.stream()
                .mapToLong(r -> ((List<?>) r.getOrDefault("accounts", List.of())).size())
                .sum();
        long closedAccounts  = results.stream()
                .flatMap(r -> ((List<Map<String, Object>>) r.getOrDefault("accounts", List.of())).stream())
                .filter(a -> "closed".equals(a.get("status")) || a.containsKey("transactionError"))
                .count();

        return ResponseEntity.ok(Map.of(
                "enrollmentCount",  enrollments.size(),
                "totalAccounts",    totalAccounts,
                "closedOrErrored",  closedAccounts,
                "enrollments",      results
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listAccounts(String accessToken) {
        String url = baseUrl + "/accounts";
        HttpEntity<Void> entity = new HttpEntity<>(basicAuthHeaders(accessToken));
        ResponseEntity<List> response = tellerRestTemplate.exchange(url, HttpMethod.GET, entity, List.class);
        return (List<Map<String, Object>>) (List<?>) response.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<?> listTransactions(String accessToken, String accountId) {
        String url = baseUrl + "/accounts/" + accountId + "/transactions";
        HttpEntity<Void> entity = new HttpEntity<>(basicAuthHeaders(accessToken));
        ResponseEntity<List> response = tellerRestTemplate.exchange(url, HttpMethod.GET, entity, List.class);
        return response.getBody();
    }

    private HttpHeaders basicAuthHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(accessToken, "");
        return headers;
    }

    private String parseErrorMessage(String body) {
        // Teller error body: {"error":{"message":"...","code":"..."}}
        if (body == null) return "unknown";
        int msgIdx = body.indexOf("\"message\":\"");
        if (msgIdx >= 0) {
            int start = msgIdx + 11;
            int end   = body.indexOf("\"", start);
            if (end > start) return body.substring(start, end);
        }
        return body.length() > 100 ? body.substring(0, 100) : body;
    }
}
