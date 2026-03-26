package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categories", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    // Excluded from equals/hashCode — User contains Set<Category>,
    // so including user here would cause Category → User → Category recursion.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false, length = 100)
    @ToString.Include
    private String name;

    @ToString.Include
    private String icon;

    @ToString.Include
    private String color;

    /**
     * When true, transactions in this category are excluded from spend totals,
     * budget tracking, and analytics. Used for transfer-type categories like
     * "Credit Card Payment" that are neither income nor real expenses.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isExcluded = false;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<Transaction> transactions = new HashSet<>();
}