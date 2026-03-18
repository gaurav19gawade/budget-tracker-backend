package com.budgettracker.controller;

import com.budgettracker.security.UserPrincipal;
import com.budgettracker.service.CategorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/categorization")
@RequiredArgsConstructor
@Slf4j
public class CategorizationController {

    private final CategorizationService categorizationService;

    /**
     * Manually triggers categorization for the authenticated user.
     * Runs the keyword pass first, then LLM for any remaining uncategorized
     * transactions. Only touches transactions with category == null —
     * manual categorizations are never overwritten.
     *
     * Returns the number of newly categorized transactions so the frontend
     * can show a meaningful result to the user.
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runCategorization(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("Manual categorization triggered for user {}", currentUser.getId());
        int categorized = categorizationService.categorizeForUser(currentUser.getId());

        return ResponseEntity.ok(Map.of(
                "categorized", categorized,
                "message", categorized > 0
                        ? categorized + " transaction" + (categorized == 1 ? "" : "s") + " categorized"
                        : "No uncategorized transactions found"
        ));
    }
}