package com.budgettracker.controller;

import com.budgettracker.dto.AnalyticsSummaryResponse;
import com.budgettracker.security.UserPrincipal;
import com.budgettracker.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/monthly-overview")
    public ResponseEntity<com.budgettracker.dto.MonthlyOverviewResponse> getMonthlyOverview(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(analyticsService.getMonthlyOverview(
                currentUser.getId(), startDate, endDate));
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        AnalyticsSummaryResponse summary = analyticsService.getSummary(
                currentUser.getId(), startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}
