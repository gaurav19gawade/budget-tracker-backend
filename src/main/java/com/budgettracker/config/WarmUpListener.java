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
 * 1. Warms the HikariCP connection pool.
 * 2. Fixes any negative amounts (stored before amount.abs() was added).
 * 3. Backfills transaction_type using Teller's original amount sign:
 *      - Before .abs() was applied, Teller sent credits as NEGATIVE amounts.
 *      - The fixNegativeAmounts() step flips them to positive.
 *      - But first we check the ORIGINAL sign: if amount was negative before
 *        flipping, Teller considered it a credit.
 *
 *    Since fixNegativeAmounts runs first and flips negatives, we can no longer
 *    use amount sign directly. Instead we use a two-pass approach:
 *      Pass 1: rows still negative (not yet fixed) → credit
 *      Pass 2: rows that are null type and were positive after fix → debit
 *
 *    For rows that already have a non-null transactionType, we never overwrite.
 *
 * 4. Removes stale duplicate Teller enrollments, keeping the newest per user+institution.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarmUpListener {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        warmUpPool();
        backfillTransactionTypesFromAmountSign();
        fixNegativeAmounts();
        correctMisclassifiedCredits();
        stampRemainingNullsAsDebit();
        removeStaleEnrollments();
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
     * MUST run BEFORE fixNegativeAmounts so we can still read the original sign.
     *
     * Teller's sign convention (before we applied abs()):
     *   negative amount = credit (money IN  — salary, Zelle received, deposits)
     *   positive amount = debit  (money OUT — purchases, payments)
     *
     * Any row still NULL that has a negative amount is a credit.
     * Any row still NULL that has a positive amount is a debit.
     * Rows with an existing non-null type are never touched.
     */
    private void backfillTransactionTypesFromAmountSign() {
        try {
            // Teller convention: POSITIVE amount = credit (money IN: salary, deposits)
            //                    NEGATIVE amount = debit  (money OUT: purchases, payments)
            // For NULL rows (inserted before transactionType column existed), restore direction
            // from the original amount sign BEFORE fixNegativeAmounts() flips them.
            int credits = jdbcTemplate.update(
                    "UPDATE transactions " +
                            "SET transaction_type = 'credit' " +
                            "WHERE transaction_type IS NULL " +
                            "  AND is_manual = false " +
                            "  AND amount > 0"
            );
            int debits = jdbcTemplate.update(
                    "UPDATE transactions " +
                            "SET transaction_type = 'debit' " +
                            "WHERE transaction_type IS NULL " +
                            "  AND is_manual = false " +
                            "  AND amount < 0"
            );
            if (credits > 0 || debits > 0) {
                log.info("Backfilled transaction types: {} credits, {} debits from amount sign", credits, debits);
            }
        } catch (Exception e) {
            log.warn("backfillTransactionTypesFromAmountSign failed (non-fatal): {}", e.getMessage());
        }
    }

    private void fixNegativeAmounts() {
        try {
            int fixed = jdbcTemplate.update(
                    "UPDATE transactions SET amount = amount * -1 WHERE amount < 0"
            );
            if (fixed > 0) log.info("Fixed {} transactions with negative amounts", fixed);
        } catch (Exception e) {
            log.warn("Negative amount fix failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Any remaining NULL after the credit backfill is a debit (positive amount = money out).
     * Only touches NULLs — never overwrites an already-set value.
     */
    /**
     * Corrects rows that were inserted with wrong transaction_type.
     * These are rows where abs() was already applied (amount > 0) but the type
     * was set to 'debit' incorrectly — specifically, Teller credits (money IN)
     * that were stored as positive amounts but tagged as debit.
     *
     * We use the running_balance pattern: credits increase balance, debits decrease.
     * Since we don't store running_balance, we use a re-sync via Teller's API.
     * As a one-time fix for existing data, we mark rows as credit where the
     * transaction_type was stamped as 'debit' by the old backfill but the row
     * belongs to a known credit pattern (ACH credits from employers/deposits).
     *
     * NOTE: This is a targeted fix. Going forward, TellerService correctly
     * derives type from rawAmount > 0 (Teller positive = money in = credit).
     * After a Sync Now, the knownIds correction path will fix recent rows.
     * Older rows (> 30 days) require a manual sync or this backfill.
     */
    /**
     * One-time migration: corrects rows that were tagged 'debit' by the old
     * backfill but are actually credits (money IN from Teller).
     *
     * Self-disabling: stores a flag in a migrations table after running so it
     * never runs again on subsequent startups — avoiding repeated table scans
     * and the risk of incorrectly flipping legitimate debits.
     */
    private void correctMisclassifiedCredits() {
        try {
            // Create a lightweight migrations tracking table if it doesn't exist
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS bt_migrations (" +
                            "  name VARCHAR(100) PRIMARY KEY, " +
                            "  ran_at TIMESTAMP DEFAULT NOW()" +
                            ")"
            );

            // Check if this migration already ran
            Integer alreadyRan = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bt_migrations WHERE name = 'correct_misclassified_credits_v1'",
                    Integer.class
            );
            if (alreadyRan != null && alreadyRan > 0) {
                log.debug("Migration correct_misclassified_credits_v1 already ran — skipping");
                return;
            }

            int corrected = jdbcTemplate.update(
                    "UPDATE transactions " +
                            "SET transaction_type = 'credit' " +
                            "WHERE transaction_type = 'debit' " +
                            "  AND is_manual = false " +
                            "  AND (" +
                            "    LOWER(merchant_name) LIKE '%payroll%'" +
                            "    OR LOWER(merchant_name) LIKE '%reg.salary%'" +
                            "    OR LOWER(merchant_name) LIKE 'orig co name:%'" +
                            "    OR LOWER(description)   LIKE 'orig co name:%'" +
                            "    OR LOWER(merchant_name) LIKE '%direct dep%'" +
                            "    OR LOWER(description)   LIKE '%payroll ppd%'" +
                            "    OR LOWER(description)   LIKE '%direct dep%'" +
                            "    OR (LOWER(merchant_name) LIKE '%zelle%' AND LOWER(merchant_name) NOT LIKE '%zelle payment to%')" +
                            "    OR (LOWER(description)   LIKE '%zelle%' AND LOWER(description)   NOT LIKE '%zelle payment to%')" +
                            "    OR LOWER(merchant_name) = 'deposit'" +
                            "    OR LOWER(description) LIKE '%ach credit%'" +
                            "  )"
            );

            // Mark as done — won't run again on next startup
            jdbcTemplate.update(
                    "INSERT INTO bt_migrations (name) VALUES ('correct_misclassified_credits_v1')"
            );

            log.info("Migration correct_misclassified_credits_v1: corrected {} transactions", corrected);
        } catch (Exception e) {
            log.warn("correctMisclassifiedCredits failed (non-fatal): {}", e.getMessage());
        }
    }

    private void stampRemainingNullsAsDebit() {
        try {
            int stamped = jdbcTemplate.update(
                    "UPDATE transactions SET transaction_type = 'debit' WHERE transaction_type IS NULL"
            );
            if (stamped > 0) log.info("Stamped {} remaining NULL rows as 'debit'", stamped);
        } catch (Exception e) {
            log.warn("stampRemainingNullsAsDebit failed (non-fatal): {}", e.getMessage());
        }
    }

    private void removeStaleEnrollments() {
        try {
            int txnsDeleted = jdbcTemplate.update(
                    "DELETE FROM transactions WHERE teller_enrollment_id IN (" +
                            "  SELECT id FROM teller_enrollments te1" +
                            "  WHERE EXISTS (" +
                            "    SELECT 1 FROM teller_enrollments te2" +
                            "    WHERE te2.user_id = te1.user_id" +
                            "      AND te2.institution_name = te1.institution_name" +
                            "      AND te2.id > te1.id" +
                            "  )" +
                            ")"
            );
            int enrollmentsDeleted = jdbcTemplate.update(
                    "DELETE FROM teller_enrollments te1" +
                            " WHERE EXISTS (" +
                            "  SELECT 1 FROM teller_enrollments te2" +
                            "  WHERE te2.user_id = te1.user_id" +
                            "    AND te2.institution_name = te1.institution_name" +
                            "    AND te2.id > te1.id" +
                            " )"
            );
            if (enrollmentsDeleted > 0) {
                log.info("Removed {} stale duplicate enrollments and {} orphaned transactions",
                        enrollmentsDeleted, txnsDeleted);
            }
        } catch (Exception e) {
            log.warn("removeStaleEnrollments failed (non-fatal): {}", e.getMessage());
        }
    }
}
