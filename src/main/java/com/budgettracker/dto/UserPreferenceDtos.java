package com.budgettracker.dto;

import com.budgettracker.model.UserPreference.ReportFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ── Request ──────────────────────────────────────────
// PUT /api/preferences
public class UserPreferenceDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreferenceRequest {
        private ReportFrequency reportFrequency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserPreferenceResponse {
        private ReportFrequency reportFrequency;
    }
}
