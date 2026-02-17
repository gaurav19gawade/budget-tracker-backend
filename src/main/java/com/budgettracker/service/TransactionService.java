package com.budgettracker.service;

import com.budgettracker.dto.TransactionRequest;
import com.budgettracker.dto.TransactionResponse;
import com.budgettracker.dto.TransactionUpdateRequest;
import com.budgettracker.exception.BadRequestException;
import com.budgettracker.exception.ResourceNotFoundException;
import com.budgettracker.model.Category;
import com.budgettracker.model.Transaction;
import com.budgettracker.model.User;
import com.budgettracker.repository.CategoryRepository;
import com.budgettracker.repository.TransactionRepository;
import com.budgettracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public TransactionResponse createTransaction(Long userId, TransactionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Verify category belongs to user
        if (!category.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        Transaction transaction = Transaction.builder()
                .user(user)
                .amount(request.getAmount())
                .date(request.getDate())
                .merchantName(request.getMerchantName())
                .description(request.getDescription())
                .category(category)
                .isManual(true)
                .pending(false)
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Created manual transaction for user {}: ${} at {}",
                userId, request.getAmount(), request.getMerchantName());

        // Check budget and send notification if needed
        notificationService.checkBudgetAndNotify(userId, category.getId(), request.getDate());

        return mapToResponse(transaction);
    }

    public List<TransactionResponse> getUserTransactions(Long userId) {
        return transactionRepository.findByUserIdOrderByDateDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<TransactionResponse> getTransactionsByDateRange(
            Long userId, LocalDate startDate, LocalDate endDate) {

        return transactionRepository.findByUserIdAndDateRange(userId, startDate, endDate).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<TransactionResponse> getTransactionsByCategory(Long userId, Long categoryId) {
        // Verify category belongs to user
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        return transactionRepository.findByUserIdAndCategoryId(userId, categoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<TransactionResponse> getTransactionsByAccountType(
            Long userId, String accountType, String accountSubtype) {

        return transactionRepository.findByUserIdWithAccountFilters(userId, accountType, accountSubtype).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse getTransactionById(Long userId, Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!transaction.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to transaction");
        }

        return mapToResponse(transaction);
    }

    @Transactional
    public TransactionResponse updateTransactionCategory(
            Long userId, Long transactionId, TransactionUpdateRequest request) {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!transaction.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to transaction");
        }

        Category newCategory = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!newCategory.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to category");
        }

        transaction.setCategory(newCategory);
        transaction = transactionRepository.save(transaction);

        log.info("Updated transaction {} category to {} for user {}",
                transactionId, newCategory.getName(), userId);

        return mapToResponse(transaction);
    }

    @Transactional
    public void deleteTransaction(Long userId, Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!transaction.getUser().getId().equals(userId)) {
            throw new BadRequestException("Unauthorized access to transaction");
        }

        // Only allow deleting manual transactions
        if (!transaction.getIsManual()) {
            throw new BadRequestException("Cannot delete automatically synced transactions");
        }

        transactionRepository.delete(transaction);
        log.info("Deleted transaction {} for user {}", transactionId, userId);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .date(transaction.getDate())
                .merchantName(transaction.getMerchantName())
                .description(transaction.getDescription())
                .categoryId(transaction.getCategory() != null ? transaction.getCategory().getId() : null)
                .categoryName(transaction.getCategory() != null ? transaction.getCategory().getName() : null)
                .isManual(transaction.getIsManual())
                .pending(transaction.getPending())
                .tellerTransactionId(transaction.getTellerTransactionId())
                .tellerAccountId(transaction.getTellerAccountId())
                .accountType(transaction.getAccountType())
                .accountSubtype(transaction.getAccountSubtype())
                .accountName(transaction.getAccountName())
                .accountLastFour(transaction.getAccountLastFour())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}