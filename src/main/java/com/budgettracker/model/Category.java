package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    private String icon;

    private String color;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    private java.util.Set<Transaction> transactions = new java.util.HashSet<>();
}