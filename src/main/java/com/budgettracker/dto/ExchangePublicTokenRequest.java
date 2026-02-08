package com.budgettracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExchangePublicTokenRequest {

    @NotBlank(message = "Public token is required")
    private String publicToken;

    private String institutionName;
}