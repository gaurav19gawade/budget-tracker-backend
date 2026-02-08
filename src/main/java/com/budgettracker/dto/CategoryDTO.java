package com.budgettracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    private Long id;

    @NotBlank(message = "Category name is required")
    private String name;

    private String icon;

    private String color;
}