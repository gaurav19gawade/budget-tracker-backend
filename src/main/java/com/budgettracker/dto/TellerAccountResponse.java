package com.budgettracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TellerAccountResponse {
    private Long id;
    private String institutionName;
    private String accountType;     // "depository" | "credit"
    private String accountSubtype;  // "checking" | "savings" | "credit_card"
    private String environment;     // "sandbox" | "development" | "production"
    private LocalDateTime connectedAt;
    private LocalDateTime lastSyncedAt;
}
