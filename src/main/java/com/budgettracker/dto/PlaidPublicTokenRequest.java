package com.budgettracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaidPublicTokenRequest {

    @NotBlank(message = "Public token is required")
    private String publicToken;

    private String institutionName;
}