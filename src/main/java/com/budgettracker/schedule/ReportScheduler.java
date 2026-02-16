package com.budgettracker.scheduler;

import com.budgettracker.model.User;
import com.budgettracker.model.UserPreference;
import com.budgettracker.model.UserPreference.ReportFrequency;
import com.budgettracker.repository.UserPreferenceRepository;
import com.budgettracker.repository.UserRepository;
import com.budgettracker.service.EmailReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportScheduler {

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final EmailReportService emailReportService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("MMM d");

    /**
     * Runs every day at 8:00 AM UTC.
     * Checks each user's preference and sends if their period is due.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendScheduledReports() {
        LocalDate today = LocalDate.now();
        log.info("Running report scheduler for {}", today);

        List<UserPreference> prefs = preferenceRepository.findAll();

        for (UserPreference pref : prefs) {
            if (pref.getReportFrequency() == ReportFrequency.NONE) continue;

            try {
                if (shouldSend(pref, today)) {
                    User user        = pref.getUser();
                    String label     = buildPeriodLabel(pref.getReportFrequency(), today);
                    emailReportService.sendBudgetReport(user, label);
                    pref.setLastReportSentAt(LocalDateTime.now());
                    preferenceRepository.save(pref);
                }
            } catch (Exception e) {
                log.error("Failed to send report for user {}: {}", pref.getUser().getId(), e.getMessage());
            }
        }
    }

    private boolean shouldSend(UserPreference pref, LocalDate today) {
        return switch (pref.getReportFrequency()) {
            case DAILY   -> true;
            case WEEKLY  -> today.getDayOfWeek() == DayOfWeek.MONDAY;
            case MONTHLY -> today.getDayOfMonth() == 1;
            case NONE    -> false;
        };
    }

    private String buildPeriodLabel(ReportFrequency freq, LocalDate today) {
        return switch (freq) {
            case DAILY   -> "Daily — " + today.format(DATE_FMT);
            case WEEKLY  -> {
                LocalDate weekStart = today.minusDays(7);
                yield "Weekly — " + weekStart.format(DATE_FMT) + " to " + today.minusDays(1).format(DATE_FMT);
            }
            case MONTHLY -> {
                LocalDate lastMonth = today.minusMonths(1);
                yield "Monthly — " + lastMonth.format(MONTH_FMT);
            }
            case NONE    -> "";
        };
    }
}
