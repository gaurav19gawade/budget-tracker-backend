package com.budgettracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs once after Spring Boot finishes startup (ApplicationReadyEvent).
 *
 * 1. Warms the HikariCP connection pool so the first real request doesn't
 *    pay the pool-init cost (especially relevant on Render where the JVM
 *    starts cold after inactivity).
 *
 * 2. Backfills transaction_type for rows that were synced before the column
 *    existed (those rows have transaction_type = NULL). Uses description/merchant
 *    keyword patterns to identify credits (salary, payroll, deposits, Zelle
 *    received, etc.) and marks everything else as 'debit'.
 *
 *    This is idempotent — safe to run on every startup, only touches NULL rows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarmUpListener {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        warmUpPool();
        fixNegativeAmounts();
        backfillTransactionTypes();
    }

    private void fixNegativeAmounts() {
        try {
            // Some transactions were synced before amount.abs() was applied in TellerService.
            // Fix any negative amounts by flipping their sign — amounts should always be positive,
            // with transactionType distinguishing debits from credits.
            int fixed = jdbcTemplate.update("""
                UPDATE transactions
                SET amount = amount * -1
                WHERE amount < 0
                """);
            if (fixed > 0) {
                log.info("Fixed {} transactions with negative amounts", fixed);
            }
        } catch (Exception e) {
            log.warn("Negative amount fix failed (non-fatal): {}", e.getMessage());
        }
    }

    private void warmUpPool() {
        try {
            long start = System.currentTimeMillis();
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.info("DB warm-up complete in {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("DB warm-up failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Backfills transaction_type for existing NULL rows.
     * Runs two UPDATE statements:
     *   1. Mark known credit patterns as 'credit'
     *   2. Mark everything still NULL as 'debit'
     *
     * Credit patterns are matched case-insensitively against merchant_name
     * and description. Covers the common cases from payroll processors,
     * Zelle/ACH transfers in, and direct deposits.
     */
    private void backfillTransactionTypes() {
        try {
            // Step 1: mark credits by keyword patterns
            int credits = jdbcTemplate.update("""
                UPDATE transactions
                SET transaction_type = 'credit'
                WHERE transaction_type IS NULL
                  AND (
                    -- Payroll / salary deposits
                    LOWER(merchant_name) LIKE '%salary%'
                    OR LOWER(merchant_name) LIKE '%payroll%'
                    OR LOWER(merchant_name) LIKE '%reg.salary%'
                    OR LOWER(merchant_name) LIKE '%payroll ppd%'
                    OR LOWER(merchant_name) LIKE '%orig co name%'
                    -- Direct deposit / generic deposit
                    OR LOWER(merchant_name) LIKE '%direct deposit%'
                    OR LOWER(merchant_name) = 'deposit'
                    OR LOWER(description) LIKE '%direct deposit%'
                    OR LOWER(description) LIKE '%payroll%'
                    OR LOWER(description) LIKE '%salary%'
                    -- Zelle / P2P received
                    OR LOWER(merchant_name) LIKE '%zelle payment from%'
                    OR LOWER(description) LIKE '%zelle payment from%'
                    -- ACH credit indicators
                    OR LOWER(description) LIKE '%ach credit%'
                    OR LOWER(description) LIKE '%ach dep%'
                    -- Refunds
                    OR LOWER(merchant_name) LIKE '%refund%'
                    OR LOWER(description) LIKE '%refund%'
                  )
                """);

            // Step 2: everything still NULL is a debit
            int debits = jdbcTemplate.update("""
                UPDATE transactions
                SET transaction_type = 'debit'
                WHERE transaction_type IS NULL
                """);

            if (credits > 0 || debits > 0) {
                log.info("Backfilled transaction_type: {} credits, {} debits", credits, debits);
            } else {
                log.debug("transaction_type backfill: nothing to update (all rows already typed)");
            }
        } catch (Exception e) {
            log.warn("transaction_type backfill failed (non-fatal): {}", e.getMessage());
        }
    }
}
