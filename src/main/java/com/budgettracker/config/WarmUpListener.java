package com.budgettracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
                UPDATE transactions
                SET amount = amount * -1
                WHERE amount < 0
                """);
            if (fixed > 0) log.info("Fixed {} transactions with negative amounts", fixed);
        } catch (Exception e) {
            log.warn("Negative amount fix failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Backfills/corrects transaction_type for all bank-synced rows.
     *
     * Re-evaluates ALL non-manual rows every startup (not just NULLs) because
     * a previous deploy may have already stamped everything as 'debit' before
     * the keyword patterns were complete. Manual transactions are always debits.
     *
     * Credit detection uses two independent signals, either is sufficient:
     *   1. Keyword match on merchant_name / description (payroll, Zelle from, deposit, etc.)
     *   2. Teller sandbox convention: credits were originally negative amounts.
     *      After fixNegativeAmounts() flips them, we've lost the sign — but the
     *      description field from Teller still contains the raw ACH description
     *      which reliably identifies the transaction type.
     */
    private void backfillTransactionTypes() {
        try {
            // Mark credits on ALL bank transactions (re-evaluate, not just NULL).
            // Keyword list covers: GE Healthcare payroll, Robinhood dividends/transfers,
            // Zelle received, direct deposits, refunds, and generic ACH credit patterns.
            int credits = jdbcTemplate.update("""
                UPDATE transactions
                SET transaction_type = 'credit'
                WHERE is_manual = false
                  AND (
                    -- Payroll / salary
                    LOWER(merchant_name) LIKE '%salary%'
                    OR LOWER(merchant_name) LIKE '%payroll%'
                    OR LOWER(merchant_name) LIKE '%reg.salary%'
                    OR LOWER(merchant_name) LIKE '%payroll ppd%'
                    OR LOWER(description)  LIKE '%payroll%'
                    OR LOWER(description)  LIKE '%salary%'
                    -- "ORIG CO NAME:" prefix = ACH originator header, almost always a credit
                    OR LOWER(merchant_name) LIKE 'orig co name:%'
                    OR LOWER(description)   LIKE 'orig co name:%'
                    -- Direct deposit
                    OR LOWER(merchant_name) LIKE '%direct deposit%'
                    OR LOWER(merchant_name) LIKE '%directdep%'
                    OR LOWER(merchant_name) = 'deposit'
                    OR LOWER(description)  LIKE '%direct deposit%'
                    OR LOWER(description)  LIKE '%ach credit%'
                    OR LOWER(description)  LIKE '%ach dep%'
                    -- Zelle / P2P received (not "Zelle payment to" which is a debit)
                    OR LOWER(merchant_name) LIKE '%zelle payment from%'
                    OR LOWER(description)   LIKE '%zelle payment from%'
                    -- Brokerage / investment credits (Robinhood, Fidelity, etc.)
                    OR LOWER(merchant_name) LIKE '%robinhood%'
                    OR LOWER(merchant_name) LIKE '%dividend%'
                    OR LOWER(description)   LIKE '%dividend%'
                    -- Refunds
                    OR LOWER(merchant_name) LIKE '%refund%'
                    OR LOWER(description)   LIKE '%refund%'
                    OR LOWER(description)   LIKE '%reversal%'
                  )
                """);

            // Everything else that's bank-synced is a debit (spending)
            int debits = jdbcTemplate.update("""
                UPDATE transactions
                SET transaction_type = 'debit'
                WHERE is_manual = false
                  AND (transaction_type IS NULL OR transaction_type <> 'credit')
                """);

            // Manual transactions are always debits
            jdbcTemplate.update("""
                UPDATE transactions
                SET transaction_type = 'debit'
                WHERE is_manual = true
                  AND transaction_type IS NULL
                """);

            log.info("transaction_type backfill: {} credits, {} debits re-evaluated", credits, debits);
        } catch (Exception e) {
            log.warn("transaction_type backfill failed (non-fatal): {}", e.getMessage());
        }
    }
}
