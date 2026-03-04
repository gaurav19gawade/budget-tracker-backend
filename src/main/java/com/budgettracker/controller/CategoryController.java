package com.budgettracker.controller;

import com.budgettracker.dto.CategoryRequest;
import com.budgettracker.dto.CategoryResponse;
import com.budgettracker.security.UserPrincipal;
import com.budgettracker.schedule.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CategoryRequest request) {

        CategoryResponse response = categoryService.createCategory(currentUser.getId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getUserCategories(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<CategoryResponse> categories = categoryService.getUserCategories(currentUser.getId());
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {

        CategoryResponse category = categoryService.getCategoryById(currentUser.getId(), id);
        return ResponseEntity.ok(category);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {

        CategoryResponse response = categoryService.updateCategory(currentUser.getId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {

        categoryService.deleteCategory(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}