package com.budgettracker.service;

import com.budgettracker.model.Budget;
import com.budgettracker.model.User;
import com.budgettracker.repository.BudgetRepository;
import com.budgettracker.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailReportService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    private static final String RESEND_API = "https://api.resend.com/emails";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    /**
     * Sends a budget summary email to the given user.
     * Called by ReportScheduler based on user's frequency preference.
     */
    public void sendBudgetReport(User user, String periodLabel) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY not set — skipping email for user {}", user.getId());
            return;
        }

        String html = buildEmailHtml(user, periodLabel);
        sendEmail(user.getEmail(), "Your Budget Summary — " + periodLabel, html);
    }

    private String buildEmailHtml(User user, String periodLabel) {
        List<Budget> budgets = budgetRepository.findByUserId(user.getId());

        StringBuilder rows = new StringBuilder();
        BigDecimal totalBudget  = BigDecimal.ZERO;
        BigDecimal totalSpent   = BigDecimal.ZERO;
        int overBudgetCount     = 0;

        for (Budget budget : budgets) {
            // Calculate spent by summing transactions in this budget's date range and category
            BigDecimal spent = transactionRepository.sumAmountByUserIdAndCategoryIdAndDateRange(
                    user.getId(),
                    budget.getCategory().getId(),
                    budget.getStartDate(),
                    budget.getEndDate()
            );
            if (spent == null) spent = BigDecimal.ZERO;

            BigDecimal amount = budget.getAmount();
            BigDecimal pct    = amount.compareTo(BigDecimal.ZERO) > 0
                    ? spent.divide(amount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            boolean isOver    = spent.compareTo(amount) > 0;

            if (isOver) overBudgetCount++;
            totalBudget = totalBudget.add(amount);
            totalSpent  = totalSpent.add(spent);

            String barColor   = isOver ? "#ff5c5c" : pct.compareTo(BigDecimal.valueOf(80)) >= 0 ? "#f59e0b" : "#00c896";
            String barWidth   = Math.min(pct.intValue(), 100) + "%";
            String statusText = isOver
                    ? "⚠️ Over by $" + spent.subtract(amount).setScale(2, RoundingMode.HALF_UP)
                    : "$" + budget.getAmount().subtract(spent).setScale(2, RoundingMode.HALF_UP) + " remaining";

            rows.append(String.format("""
                <tr>
                  <td style="padding:12px 0;border-bottom:1px solid #1e2130;">
                    <div style="display:flex;justify-content:space-between;margin-bottom:6px;">
                      <span style="color:#e8eaf0;font-size:14px;font-weight:500;">%s</span>
                      <span style="color:#8b8fa8;font-size:12px;font-family:monospace;">$%s / $%s</span>
                    </div>
                    <div style="background:#1e2130;border-radius:4px;height:6px;overflow:hidden;">
                      <div style="background:%s;height:6px;width:%s;border-radius:4px;"></div>
                    </div>
                    <div style="margin-top:4px;font-size:11px;color:%s;">%s</div>
                  </td>
                </tr>
                """,
                    budget.getCategory() != null ? budget.getCategory().getName() : "Uncategorized",
                    spent.setScale(2, RoundingMode.HALF_UP),
                    amount.setScale(2, RoundingMode.HALF_UP),
                    barColor, barWidth,
                    isOver ? "#ff5c5c" : "#4a4d5e",
                    statusText
            ));
        }

        BigDecimal remaining = totalBudget.subtract(totalSpent);

        String overBudgetBanner = overBudgetCount > 0 ? String.format("""
            <div style="background:#2a1515;border:1px solid #ff5c5c33;border-radius:12px;padding:12px 16px;margin-bottom:24px;">
              <p style="color:#ff5c5c;margin:0;font-size:14px;">
                ⚠️ <strong>%d %s</strong> over budget this period
              </p>
            </div>
            """, overBudgetCount, overBudgetCount == 1 ? "category is" : "categories are") : "";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#0f1117;font-family:'DM Sans',Arial,sans-serif;">
              <div style="max-width:560px;margin:0 auto;padding:32px 16px;">

                <!-- Header -->
                <div style="margin-bottom:32px;">
                  <div style="display:inline-flex;align-items:center;gap:10px;margin-bottom:16px;">
                    <div style="width:32px;height:32px;background:#00c896;border-radius:10px;display:flex;align-items:center;justify-content:center;">
                      <span style="color:#0a0d14;font-weight:700;font-size:16px;">$</span>
                    </div>
                    <span style="color:#ffffff;font-weight:600;font-size:16px;">BudgetTracker</span>
                  </div>
                  <h1 style="color:#ffffff;font-size:22px;font-weight:600;margin:0 0 4px;">
                    Budget Report
                  </h1>
                  <p style="color:#4a4d5e;font-size:14px;margin:0;">%s · %s</p>
                </div>

                <!-- Summary cards -->
                <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px;margin-bottom:24px;">
                  <div style="background:#13151f;border:1px solid #1e2130;border-radius:12px;padding:16px;">
                    <p style="color:#4a4d5e;font-size:11px;text-transform:uppercase;letter-spacing:0.05em;margin:0 0 4px;">Budget</p>
                    <p style="color:#fff;font-size:20px;font-weight:600;font-family:monospace;margin:0;">$%s</p>
                  </div>
                  <div style="background:#13151f;border:1px solid #1e2130;border-radius:12px;padding:16px;">
                    <p style="color:#4a4d5e;font-size:11px;text-transform:uppercase;letter-spacing:0.05em;margin:0 0 4px;">Spent</p>
                    <p style="color:#fff;font-size:20px;font-weight:600;font-family:monospace;margin:0;">$%s</p>
                  </div>
                  <div style="background:#13151f;border:1px solid #1e2130;border-radius:12px;padding:16px;">
                    <p style="color:#4a4d5e;font-size:11px;text-transform:uppercase;letter-spacing:0.05em;margin:0 0 4px;">Remaining</p>
                    <p style="color:%s;font-size:20px;font-weight:600;font-family:monospace;margin:0;">$%s</p>
                  </div>
                </div>

                %s

                <!-- Budget breakdown -->
                <div style="background:#13151f;border:1px solid #1e2130;border-radius:16px;padding:20px;margin-bottom:24px;">
                  <h2 style="color:#fff;font-size:14px;font-weight:600;margin:0 0 16px;">Budget Breakdown</h2>
                  <table style="width:100%%;border-collapse:collapse;">
                    %s
                  </table>
                </div>

                <!-- Footer -->
                <div style="text-align:center;">
                  <p style="color:#2e3140;font-size:12px;margin:0 0 8px;">
                    You're receiving this because you enabled periodic reports in BudgetTracker.
                  </p>
                  <p style="color:#2e3140;font-size:12px;margin:0;">
                    Manage your preferences at <a href="https://budget-tracker-frontend-rv8c.vercel.app/settings" style="color:#00c896;">Settings</a>
                  </p>
                </div>
              </div>
            </body>
            </html>
            """,
                periodLabel,
                LocalDate.now().format(DATE_FMT),
                totalBudget.setScale(2, RoundingMode.HALF_UP),
                totalSpent.setScale(2, RoundingMode.HALF_UP),
                remaining.compareTo(BigDecimal.ZERO) < 0 ? "#ff5c5c" : "#00c896",
                remaining.abs().setScale(2, RoundingMode.HALF_UP),
                overBudgetBanner,
                rows.toString()
        );
    }

    private void sendEmail(String to, String subject, String html) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from",    fromEmail);
            body.put("to",      List.of(to));
            body.put("subject", subject);
            body.put("html",    html);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(RESEND_API, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Report email sent to {}", to);
            } else {
                log.error("Failed to send email to {}: {}", to, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error sending email to {}: {}", to, e.getMessage());
        }
    }
}
