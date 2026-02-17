package com.budgettracker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * TEMPORARY TEST CONTROLLER - Remove after testing Teller API responses
 * This helps us understand what data Teller returns for accounts and transactions
 *
 * IMPORTANT: Uses tellerRestTemplate which includes mTLS client certificates
 */
@RestController
@RequestMapping("/api/test/teller")
@RequiredArgsConstructor
@Slf4j
public class TellerTestController {

    @Qualifier("tellerRestTemplate")  // THIS IS CRITICAL - uses mTLS-configured RestTemplate
    private final RestTemplate tellerRestTemplate;

    @Value("${teller.base-url}")
    private String baseUrl;

    /**
     * Test endpoint to see raw Teller account data
     * Call this with: GET /api/test/teller/accounts?accessToken=YOUR_ACCESS_TOKEN
     */
    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> testGetAccounts(
            @RequestParam String accessToken) {

        try {
            String url = baseUrl + "/accounts";

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setBasicAuth(accessToken, "");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = tellerRestTemplate.exchange(url, HttpMethod.GET, entity, List.class);

            List<Map<String, Object>> accounts = (List<Map<String, Object>>) (List<?>) response.getBody();

            log.info("=== TELLER ACCOUNTS RESPONSE ===");
            log.info("Total accounts: {}", accounts.size());

            for (int i = 0; i < accounts.size(); i++) {
                Map<String, Object> account = accounts.get(i);
                log.info("--- Account {} ---", i + 1);
                log.info("Full account data: {}", account);
                log.info("Account ID: {}", account.get("id"));
                log.info("Account Name: {}", account.get("name"));
                log.info("Account Type: {}", account.get("type"));
                log.info("Account Subtype: {}", account.get("subtype"));
                log.info("Institution: {}", account.get("institution"));
                log.info("Currency: {}", account.get("currency"));
                log.info("Status: {}", account.get("status"));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "totalAccounts", accounts.size(),
                    "accounts", accounts
            ));

        } catch (Exception e) {
            log.error("Error testing Teller API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Test endpoint to see raw Teller transaction data for a specific account
     * Call this with: GET /api/test/teller/transactions?accessToken=YOUR_ACCESS_TOKEN&accountId=ACCOUNT_ID
     */
    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> testGetTransactions(
            @RequestParam String accessToken,
            @RequestParam String accountId) {

        try {
            String url = baseUrl + "/accounts/" + accountId + "/transactions";

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setBasicAuth(accessToken, "");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<List> response = tellerRestTemplate.exchange(url, HttpMethod.GET, entity, List.class);

            List<Map<String, Object>> transactions = (List<Map<String, Object>>) (List<?>) response.getBody();

            log.info("=== TELLER TRANSACTIONS RESPONSE ===");
            log.info("Total transactions: {}", transactions.size());

            // Show first 3 transactions in detail
            int showCount = Math.min(3, transactions.size());
            for (int i = 0; i < showCount; i++) {
                Map<String, Object> txn = transactions.get(i);
                log.info("--- Transaction {} ---", i + 1);
                log.info("Full transaction data: {}", txn);
                log.info("ID: {}", txn.get("id"));
                log.info("Amount: {}", txn.get("amount"));
                log.info("Date: {}", txn.get("date"));
                log.info("Description: {}", txn.get("description"));
                log.info("Merchant Name: {}", txn.get("merchant_name"));
                log.info("Type: {}", txn.get("type"));
                log.info("Status: {}", txn.get("status"));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "accountId", accountId,
                    "totalTransactions", transactions.size(),
                    "transactions", transactions
            ));

        } catch (Exception e) {
            log.error("Error testing Teller transactions API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }
}