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
 * 2. Fixes any negative amounts from before amount.abs() was added.
 * 3. Stamps NULL transaction_type rows as 'debit' as a safe default.
 *    ONLY touches rows where transaction_type IS NULL — never overwrites
 *    an already-set value (credit or debit).
 * 4. Removes stale duplicate Teller enrollments (same user + institution).
 *    Keeps the newest enrollment per user+institution, deletes older ones
 *    along with their orphaned transactions. This prevents duplicate key
 *    errors when two enrollments try to insert the same teller_transaction_id.
 *    After deletion, the next scheduled sync re-inserts those transactions
 *    with the correct type derived from Teller's amount sign.
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

    private void stampNullTypes() {
        try {
            int stamped = jdbcTemplate.update(
                    "UPDATE transactions SET transaction_type = 'debit' WHERE transaction_type IS NULL"
            );
            if (stamped > 0) log.info("Stamped {} NULL transaction_type rows as 'debit'", stamped);
        } catch (Exception e) {
            log.warn("stampNullTypes failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Removes older duplicate Teller enrollments where the same user has
     * multiple enrollments for the same institution. Keeps the highest ID
     * (most recently created) and deletes the rest along with their transactions.
     *
     * Root cause: if a user connects Chase, disconnects, and reconnects, they
     * get two enrollment rows. The scheduled sync runs both, and when enrollment
     * 3 tries to insert transactions already saved by enrollment 5, it hits the
     * unique constraint on teller_transaction_id.
     */
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
