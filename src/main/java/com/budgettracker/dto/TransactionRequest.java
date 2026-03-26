package com.budgettracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @NotBlank(message = "Merchant name is required")
    private String merchantName;

    private String description;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    /**
     * "debit" = money out (default for manual entries), "credit" = money in (salary, income).
     * Null is treated as "debit" in the service layer so existing callers are unaffected.
     */
    @Pattern(regexp = "^(debit|credit)$", message = "transactionType must be 'debit' or 'credit'")
    private String transactionType;
}