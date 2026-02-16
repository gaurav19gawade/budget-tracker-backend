package com.budgettracker.controller;

import com.budgettracker.dto.UserPreferenceDtos.UserPreferenceRequest;
import com.budgettracker.dto.UserPreferenceDtos.UserPreferenceResponse;
import com.budgettracker.model.User;
import com.budgettracker.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<UserPreferenceResponse> getPreference(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(preferenceService.getPreference(user.getId()));
    }

    @PutMapping
    public ResponseEntity<UserPreferenceResponse> updatePreference(
            @AuthenticationPrincipal User user,
            @RequestBody UserPreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.updatePreference(user.getId(), request));
    }
}
