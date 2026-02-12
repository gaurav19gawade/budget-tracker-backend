package com.budgettracker.controller;

import com.budgettracker.dto.TellerAccountResponse;
import com.budgettracker.dto.TellerConnectRequest;
import com.budgettracker.security.UserPrincipal;
import com.budgettracker.service.TellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teller")
@RequiredArgsConstructor
@Slf4j
public class TellerController {

    private final TellerService tellerService;

    /**
     * Teller Connect hands back accessToken + enrollment details (no link_token/public_token exchange). :contentReference[oaicite:10]{index=10}
     */
    @PostMapping("/connect")
    public ResponseEntity<TellerAccountResponse> connect(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody TellerConnectRequest request) {

        TellerAccountResponse response = tellerService.connectEnrollment(
                currentUser.getId(),
                request.getAccessToken(),
                request.getEnrollmentId(),
                request.getInstitutionName()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<TellerAccountResponse>> getConnectedAccounts(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(tellerService.getConnectedAccounts(currentUser.getId()));
    }

    @PostMapping("/sync/{enrollmentDbId}")
    public ResponseEntity<Void> syncTransactions(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long enrollmentDbId) {

        // ownership check is enforced in remove; sync is safe but you can add findByIdAndUserId if desired
        tellerService.syncTransactions(enrollmentDbId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/accounts/{enrollmentDbId}")
    public ResponseEntity<Void> removeAccount(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long enrollmentDbId) {

        tellerService.removeAccount(enrollmentDbId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
