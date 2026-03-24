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
 * Kept intentionally minimal now that transaction data has been wiped and
 * re-synced clean. TellerService is the authoritative source of truth for
 * transaction_type — no backfill logic needed here.
 *
 * If future one-time migrations are needed, add them with a bt_migrations
 * guard so they only run once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarmUpListener {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        warmUpPool();
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
     * Removes older duplicate Teller enrollments (same user + institution).
     * Keeps the highest ID (most recently created) and deletes the rest.
     * Prevents duplicate key errors when two enrollments sync the same transactions.
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
