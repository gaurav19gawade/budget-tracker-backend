package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "plaid_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true, nullable = false)
    private String plaidItemId;

    @Column(nullable = false)
    private String plaidAccessToken;

    private String institutionName;

    private LocalDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}