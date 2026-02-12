package com.budgettracker.schedule;

import com.budgettracker.model.TellerEnrollment;
import com.budgettracker.repository.TellerEnrollmentRepository;
import com.budgettracker.service.TellerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionSyncScheduler {

    private final TellerEnrollmentRepository tellerEnrollmentRepository;
    private final TellerService tellerService;

    /**
     * Sync transactions for all connected Teller enrollments every 6 hours
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void syncAllTransactions() {
        log.info("Starting scheduled transaction sync...");

        List<TellerEnrollment> all = tellerEnrollmentRepository.findAll();
        log.info("Found {} Teller enrollments to sync", all.size());

        int successCount = 0;
        int failureCount = 0;

        for (TellerEnrollment e : all) {
            try {
                log.info("Syncing transactions for Teller enrollment {} ({})", e.getId(), e.getInstitutionName());
                tellerService.syncTransactions(e.getId());
                successCount++;
            } catch (Exception ex) {
                log.error("Failed to sync transactions for Teller enrollment {}", e.getId(), ex);
                failureCount++;
            }
        }

        log.info("Transaction sync completed. Success: {}, Failures: {}", successCount, failureCount);
    }
}
