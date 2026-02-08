package com.budgettracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionUpdateRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;
}