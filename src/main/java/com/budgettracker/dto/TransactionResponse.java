package com.budgettracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private Long id;
    private BigDecimal amount;
    private LocalDate date;
    private String merchantName;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Boolean isManual;
    private Boolean pending;
    private String tellerTransactionId;

    // Account information
    private String tellerAccountId;
    private String accountType;        // "credit", "depository"
    private String accountSubtype;     // "credit_card", "checking", "savings"
    private String accountName;        // "Freedom Unlimited", "TOTAL CHECKING"
    private String accountLastFour;    // Last 4 digits

    private LocalDateTime createdAt;
}