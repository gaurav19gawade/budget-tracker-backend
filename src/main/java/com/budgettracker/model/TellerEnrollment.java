package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "teller_enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TellerEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true, nullable = false)
    private String enrollmentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    private String institutionName;

    // e.g. "depository", "credit"
    private String accountType;

    // e.g. "checking", "savings", "credit_card"
    private String accountSubtype;

    private LocalDateTime lastSyncedAt;

    private String environment;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Back-reference for cascade delete — when enrollment is deleted,
    // all linked transactions are deleted too (resolves FK constraint)
    @OneToMany(mappedBy = "tellerEnrollment", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Transaction> transactions;
}
