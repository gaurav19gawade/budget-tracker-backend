package com.budgettracker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Ultra-lightweight ping endpoint used by keep-alive cron jobs (e.g. cron-job.org)
 * to prevent Render's free tier from spinning down the JVM.
 *
 * Intentionally does NO database work — we want this to respond in < 5ms
 * so the cron ping doesn't wake a sleeping DB connection pool.
 * Spring Actuator's /actuator/health is also available but this is simpler
 * and has no extra dependency surface.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
