package com.budgettracker.scheduler;

import com.budgettracker.model.PlaidItem;
import com.budgettracker.repository.PlaidItemRepository;
import com.budgettracker.service.PlaidService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionSyncScheduler {

    private final PlaidItemRepository plaidItemRepository;
    private final PlaidService plaidService;

    /**
     * Sync transactions for all connected Plaid items every 6 hours
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000) // 6 hours in milliseconds
    public void syncAllTransactions() {
        log.info("Starting scheduled transaction sync...");

        List<PlaidItem> allItems = plaidItemRepository.findAll();
        log.info("Found {} Plaid items to sync", allItems.size());

        int successCount = 0;
        int failureCount = 0;

        for (PlaidItem item : allItems) {
            try {
                log.info("Syncing transactions for Plaid item {} ({})",
                        item.getId(), item.getInstitutionName());
                plaidService.syncTransactions(item.getId());
                successCount++;
            } catch (IOException e) {
                log.error("Failed to sync transactions for Plaid item {}", item.getId(), e);
                failureCount++;
            } catch (Exception e) {
                log.error("Unexpected error syncing Plaid item {}", item.getId(), e);
                failureCount++;
            }
        }

        log.info("Transaction sync completed. Success: {}, Failures: {}", successCount, failureCount);
    }

    /**
     * Optional: Sync transactions on startup (useful for testing)
     * Comment this out in production if you don't want sync on startup
     */
    // @Scheduled(initialDelay = 30000) // 30 seconds after startup
    // public void initialSync() {
    //     log.info("Running initial transaction sync...");
    //     syncAllTransactions();
    // }
}