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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    // All ManyToOne associations excluded — each points back to an entity
    // that holds a collection of Transactions, creating a recursion cycle.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teller_enrollment_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TellerEnrollment tellerEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Category category;

    @Column(unique = true)
    @ToString.Include
    private String tellerTransactionId;

    private String tellerAccountId;

    @Column(name = "account_type", length = 50)
    @ToString.Include
    private String accountType;

    @Column(name = "account_subtype", length = 50)
    @ToString.Include
    private String accountSubtype;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "account_last_four", length = 4)
    private String accountLastFour;

    @Column(nullable = false, precision = 10, scale = 2)
    @ToString.Include
    private BigDecimal amount;

    @Column(nullable = false)
    @ToString.Include
    private LocalDate date;

    @ToString.Include
    private String merchantName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean isManual = false;

    @Column(nullable = false)
    private Boolean pending = false;

    /**
     * Teller transaction type: "debit" (money out) or "credit" (money in).
     * Null for manually-entered transactions which are always treated as debits.
     */
    @Column(name = "transaction_type", length = 10)
    private String transactionType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}