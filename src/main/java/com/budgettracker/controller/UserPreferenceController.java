package com.budgettracker.controller;

import com.budgettracker.dto.UserPreferenceDtos.UserPreferenceRequest;
import com.budgettracker.dto.UserPreferenceDtos.UserPreferenceResponse;
import com.budgettracker.security.UserPrincipal;
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
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(preferenceService.getPreference(currentUser.getId()));
    }

    @PutMapping
    public ResponseEntity<UserPreferenceResponse> updatePreference(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody UserPreferenceRequest request) {
        return ResponseEntity.ok(preferenceService.updatePreference(currentUser.getId(), request));
    }
}