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
public class PlaidAccountResponse {
    private Long id;
    private String institutionName;
    private LocalDateTime connectedAt;
    private LocalDateTime lastSyncedAt;
}