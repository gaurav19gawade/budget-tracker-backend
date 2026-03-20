package com.budgettracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fires once after Spring Boot finishes startup (ApplicationReadyEvent).
 *
 * On Render, the JVM starts but the first real HTTP request still incurs
 * latency while Hibernate initialises its session factory and HikariCP
 * opens its connection pool to PostgreSQL.
 *
 * This listener pre-warms both by running a trivial query immediately on
 * startup, so the pool is hot before the first user request arrives.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarmUpListener {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            long start = System.currentTimeMillis();
            // SELECT 1 is the cheapest possible query — just validates the connection
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.info("DB warm-up complete in {}ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            // Don't crash startup if DB is temporarily unavailable
            log.warn("DB warm-up failed (non-fatal): {}", e.getMessage());
        }
    }
}
