package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportFrequency reportFrequency = ReportFrequency.NONE;

    // Track when we last sent a report so we don't double-send
    private LocalDateTime lastReportSentAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum ReportFrequency {
        DAILY, WEEKLY, MONTHLY, NONE
    }
}
