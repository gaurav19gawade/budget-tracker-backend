package com.budgettracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs once after Spring Boot finishes startup.
 *
 * 1. Warms the HikariCP connection pool so the first real request doesn't
 *    pay the pool-init cost on Render cold starts.
 *
 * 2. Fixes any negative amounts left over from before amount.abs() was added.
 *
 * 3. Stamps any remaining NULL transaction_type rows as 'debit' so queries
 *    that filter on this column behave predictably.
 *    NOTE: The authoritative fix for wrong transaction types is a Teller re-sync
 *    (Settings → Sync Now), which overwrites the type with Teller's actual value.
 *    Keyword guessing has been removed — it was unreliable.
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
        stampNullTypes();
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

    private void fixNegativeAmounts() {
        try {
            int fixed = jdbcTemplate.update("""
                UPDATE transactions SET amount = amount * -1 WHERE amount < 0
                """);
            if (fixed > 0) log.info("Fixed {} transactions with negative amounts", fixed);
        } catch (Exception e) {
            log.warn("Negative amount fix failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Any row still NULL after previous migrations gets stamped 'debit' as a safe default.
     * The correct type will be set by Teller on the next re-sync.
     */
    private void stampNullTypes() {
        try {
            int stamped = jdbcTemplate.update("""
                UPDATE transactions SET transaction_type = 'debit' WHERE transaction_type IS NULL
                """);
            if (stamped > 0) log.info("Stamped {} NULL transaction_type rows as 'debit'", stamped);
        } catch (Exception e) {
            log.warn("stampNullTypes failed (non-fatal): {}", e.getMessage());
        }
    }
}
