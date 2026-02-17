package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_user_date", columnList = "user_id,date"),
        @Index(name = "idx_category", columnList = "category_id"),
        @Index(name = "idx_account_type", columnList = "account_type"),
        @Index(name = "idx_account_subtype", columnList = "account_subtype")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teller_enrollment_id")
    private TellerEnrollment tellerEnrollment;

    @Column(unique = true)
    private String tellerTransactionId;

    // Account information from Teller
    private String tellerAccountId;

    @Column(name = "account_type", length = 50)
    private String accountType;  // "credit", "depository"

    @Column(name = "account_subtype", length = 50)
    private String accountSubtype;  // "credit_card", "checking", "savings"

    @Column(name = "account_name")
    private String accountName;  // "Freedom Unlimited", "TOTAL CHECKING", etc.

    @Column(name = "account_last_four", length = 4)
    private String accountLastFour;  // Last 4 digits for display

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    private String merchantName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private Boolean isManual = false;

    @Column(nullable = false)
    private Boolean pending = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}