package com.budgettracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TellerConnectRequest {

    @NotBlank(message = "Access token is required")
    private String accessToken;

    @NotBlank(message = "Enrollment id is required")
    private String enrollmentId;

    private String institutionName;
}
