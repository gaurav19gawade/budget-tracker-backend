package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "budget_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AlertType alertType;

    @CreationTimestamp
    private LocalDateTime sentAt;

    public enum AlertType {
        APPROACHING, EXCEEDED
    }
}