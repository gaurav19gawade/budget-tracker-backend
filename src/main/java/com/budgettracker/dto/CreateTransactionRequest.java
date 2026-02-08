package com.budgettracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateTransactionRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Date is required")
    private LocalDate date;

    private String merchantName;

    private String description;

    @NotNull(message = "Category ID is required")
    private Long categoryId;
}