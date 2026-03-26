package com.budgettracker.service;

import com.budgettracker.model.Category;
import com.budgettracker.model.Transaction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Uses the Anthropic Claude API to categorize transactions that the keyword
 * matcher couldn't handle. Sends all unmatched transactions in a single
 * batched request to minimize API calls and cost.
 *
 * Design:
 *  - Only called for transactions with category == null after keyword pass
 *  - Sends merchant name + description + amount to Claude
 *  - Claude returns a JSON map of transactionId → categoryName
 *  - Category names that don't match the user's actual categories are ignored
 *  - If the API is unavailable or returns garbage, we log and move on (non-fatal)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnthropicCategorizationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Value("${anthropic.model:claude-haiku-4-5-20251001}")
    private String model;

    // Haiku is used by default — it's the fastest and cheapest model, and
    // categorization is a simple classification task that doesn't need Sonnet/Opus.
    // Categorizing 100 transactions costs roughly $0.001 with Haiku.

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final int    MAX_TOKENS = 1024;
    private static final int    BATCH_SIZE = 50; // transactions per API call

    /**
     * Categorizes a list of transactions using Claude.
     * Returns a map of transaction ID → matched Category (only for confident matches).
     * Transactions that Claude can't confidently categorize are omitted from the map.
     */
    public Map<Long, Category> categorize(
            List<Transaction> transactions,
            List<Category> userCategories) {

        if (!isEnabled()) {
            log.debug("Anthropic API key not configured — skipping LLM categorization");
            return Collections.emptyMap();
        }

        if (transactions.isEmpty() || userCategories.isEmpty()) {
            return Collections.emptyMap();
        }

        // Build lookup: lowercase name → Category entity
        Map<String, Category> categoryLookup = userCategories.stream()
                .collect(Collectors.toMap(
                        c -> c.getName().toLowerCase().trim(),
                        c -> c,
                        (a, b) -> a // keep first on collision
                ));

        List<String> categoryNames = userCategories.stream()
                .map(Category::getName)
                .collect(Collectors.toList());

        Map<Long, Category> results = new HashMap<>();

        // Process in batches to avoid hitting token limits
        for (int i = 0; i < transactions.size(); i += BATCH_SIZE) {
            List<Transaction> batch = transactions.subList(i, Math.min(i + BATCH_SIZE, transactions.size()));
            try {
                Map<Long, Category> batchResults = categorizeBatch(batch, categoryNames, categoryLookup);
                results.putAll(batchResults);
            } catch (Exception e) {
                log.warn("LLM categorization batch failed (transactions {}-{}): {}",
                        i, i + batch.size(), e.getMessage());
                // Non-fatal — continue without categorizing this batch
            }
        }

        log.info("LLM categorized {}/{} transactions", results.size(), transactions.size());
        return results;
    }

    private Map<Long, Category> categorizeBatch(
            List<Transaction> batch,
            List<String> categoryNames,
            Map<String, Category> categoryLookup) throws Exception {

        String prompt = buildPrompt(batch, categoryNames);
        String response = callClaude(prompt);
        return parseResponse(response, categoryLookup);
    }

    private String buildPrompt(List<Transaction> transactions, List<String> categoryNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Categorize each bank transaction into exactly one of these categories:\n");
        sb.append(String.join(", ", categoryNames));
        sb.append("\n\n");

        sb.append("RULES (read carefully before categorizing):\n\n");

        sb.append("EXCLUDED categories (transfers/excluded from budget totals):\n");
        sb.append("- \"Credit Card Payment\": ONLY for scheduled CC bill payments. ");
        sb.append("Examples: CHASE CREDIT CRD AUTOPAY, AMEX EPAYMENT, CAPITAL ONE AUTOPAY.\n");
        sb.append("- \"Transfer\": Online transfers between own bank accounts ");
        sb.append("(e.g. 'Online Transfer to CHK', 'Online Transfer from SAV').\n");
        sb.append("- \"Salary\": Payroll deposits (PPD PAYROLL, REG.SALARY, DIRECT DEPOSIT from employer).\n");
        sb.append("- \"Investments\": Robinhood, Fidelity (FID BKG SVC), Vanguard, Schwab, Aspora.\n");
        sb.append("- \"Refund\": Credits on credit card that are returns/refunds, RETURN OF POSTED CHECK.\n\n");

        sb.append("EXPENSE categories:\n");
        sb.append("- \"Mortgage\": M&T Mortgage, NSM/Mr.Cooper, PL*PatricianAsso rent payments.\n");
        sb.append("- \"Childcare\": Goddard School, Cadence Education, daycare centers.\n");
        sb.append("- \"Nanny\": use null — Zelle to individuals cannot be auto-categorized.\n");
        sb.append("- \"Car Payment\": HMF HMFUSA (Hyundai), Toyota Motor Credit, Ford Motor Credit.\n");
        sb.append("- \"Utilities\": National Grid (NGRID06), Eversource, Verizon, Comcast.\n");
        sb.append("- \"Gas\": Citgo, Shell, Exxon, Costco Gas (NOT Costco warehouse).\n");
        sb.append("- \"Transportation\": Lyft, Uber, EZPass, SpotHero, ParkM, Logan Parking.\n");
        sb.append("- \"Travel\": Delta Air, United, Southwest, JetBlue, Airbnb, hotels.\n");
        sb.append("- \"Healthcare\": PatientFi, CareCredit, medical offices, hospitals.\n");
        sb.append("- \"Remittance\": Western Union, MoneyGram, Remitly, Wise international transfers.\n");
        sb.append("- \"HOA\": Dean Farm Ridge, homeowners association payments.\n");
        sb.append("- \"Bank Fee\": Overdraft fee, insufficient funds fee, monthly account fee.\n");
        sb.append("- \"Donations\": World Food Program, Red Cross, UNICEF, charity.\n");
        sb.append("- \"Food Delivery\": DoorDash, Uber Eats, Grubhub, Tiffin meal service.\n");
        sb.append("- \"Groceries\": Whole Foods, Wegmans, Shaw's, Apna Bazar, Costco warehouse.\n");
        sb.append("- \"Shopping\": Amazon, Macy's, Sephora, Kohl's, H&M, Klarna BNPL payments.\n");
        sb.append("- \"Entertainment\": Netflix, AMC, Fandango, Peloton, Martini's Pickleball.\n\n");

        sb.append("IMPORTANT:\n");
        sb.append("- Use null for Zelle payments to individuals — impossible to auto-categorize.\n");
        sb.append("- Use null rather than guessing.\n");
        sb.append("- Costco GAS = Gas. Costco WHSE (warehouse) = Groceries.\n\n");

        sb.append("Transactions (id | merchant | description | amount):\n");

        for (Transaction tx : transactions) {
            sb.append(tx.getId()).append(" | ");
            sb.append(tx.getMerchantName() != null ? tx.getMerchantName() : "Unknown");
            sb.append(" | ");
            sb.append(tx.getDescription() != null ? tx.getDescription() : "");
            sb.append(" | $").append(tx.getAmount());
            sb.append("\n");
        }

        sb.append("\nRespond with ONLY a JSON object mapping transaction id to category name.\n");
        sb.append("Use null for transactions you cannot confidently categorize.\n");
        sb.append("Example: {\"123\": \"Groceries\", \"124\": \"Transportation\", \"125\": null}\n");
        sb.append("Category names must match exactly (case-sensitive) from the list above.");

        return sb.toString();
    }

    private String callClaude(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                API_URL, HttpMethod.POST, entity, JsonNode.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Anthropic API returned " + response.getStatusCode());
        }

        // Extract text content from response
        JsonNode content = response.getBody().path("content");
        if (!content.isArray() || content.isEmpty()) {
            throw new RuntimeException("Unexpected Anthropic response structure");
        }

        return content.get(0).path("text").asText();
    }

    private Map<Long, Category> parseResponse(
            String responseText,
            Map<String, Category> categoryLookup) throws Exception {

        // Strip any markdown code fences Claude might add despite instructions
        String json = responseText.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();

        Map<String, String> raw = objectMapper.readValue(json, new TypeReference<>() {});

        Map<Long, Category> result = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) continue;

            try {
                Long txId = Long.parseLong(entry.getKey());
                String catName = entry.getValue().toLowerCase().trim();
                Category cat = categoryLookup.get(catName);
                if (cat != null) {
                    result.put(txId, cat);
                } else {
                    log.debug("LLM suggested unknown category '{}' for tx {} — ignoring",
                            entry.getValue(), txId);
                }
            } catch (NumberFormatException e) {
                log.debug("Could not parse transaction id '{}' from LLM response", entry.getKey());
            }
        }

        return result;
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}