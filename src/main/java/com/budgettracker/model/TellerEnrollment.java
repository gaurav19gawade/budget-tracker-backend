package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    /**
     * Teller enrollment id (enr_xxx) returned by Teller Connect.
     * Enrollment is Teller’s equivalent of a user login at an FI. :contentReference[oaicite:5]{index=5}
     */
    @Column(unique = true, nullable = false)
    private String enrollmentId;

    /**
     * Teller access token returned by Teller Connect.
     * Stored encrypted-at-rest in a real system (KMS/Vault), but kept as-is here for your project.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    private String institutionName;

    private LocalDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
