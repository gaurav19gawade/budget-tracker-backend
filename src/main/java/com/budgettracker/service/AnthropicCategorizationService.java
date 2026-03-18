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
        sb.append("Categorize each transaction into exactly one of these categories:\n");
        sb.append(String.join(", ", categoryNames));
        sb.append("\n\n");
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
        sb.append("Example: {\"123\": \"Groceries\", \"124\": \"Transport\", \"125\": null}\n");
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