package com.budgettracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaidAccountDTO {

    private Long id;
    private String institutionName;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
}