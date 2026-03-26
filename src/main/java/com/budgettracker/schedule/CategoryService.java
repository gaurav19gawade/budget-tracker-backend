package com.budgettracker.schedule;

import com.budgettracker.dto.CategoryRequest;
import com.budgettracker.dto.CategoryResponse;
import com.budgettracker.exception.BadRequestException;
import com.budgettracker.exception.ResourceNotFoundException;
import com.budgettracker.model.Category;
import com.budgettracker.model.User;
import com.budgettracker.repository.CategoryRepository;
import com.budgettracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public CategoryResponse createCategory(Long userId, CategoryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check for duplicate category name
        if (categoryRepository.existsByUserIdAndName(userId, request.getName())) {
            throw new BadRequestException("Category with name '" + request.getName() + "' already exists");
        }

        Category category = Category.builder()
                .user(user)
                .name(request.getName())
                .icon(request.getIcon())
                .color(request.getColor())
                .isExcluded(request.getIsExcluded() != null ? request.getIsExcluded() : false)
                .build();

        category = categoryRepository.save(category);
        log.info("Created category '{}' for user {}", category.getName(), userId);

        return mapToResponse(category);
    }

    public List<CategoryResponse> getUserCategories(Long userId) {
        return categoryRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CategoryResponse getCategoryById(Long userId, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        return mapToResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(Long userId, Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        // Check for duplicate name (excluding current category)
        categoryRepository.findByUserIdAndName(userId, request.getName())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(categoryId)) {
                        throw new BadRequestException("Category with name '" + request.getName() + "' already exists");
                    }
                });

        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setColor(request.getColor());
        if (request.getIsExcluded() != null) {
            category.setIsExcluded(request.getIsExcluded());
        }

        category = categoryRepository.save(category);
        log.info("Updated category {} for user {}", categoryId, userId);

        return mapToResponse(category);
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        categoryRepository.delete(category);
        log.info("Deleted category {} for user {}", categoryId, userId);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .icon(category.getIcon())
                .color(category.getColor())
                .isExcluded(category.getIsExcluded() != null ? category.getIsExcluded() : false)
                .build();
    }
}