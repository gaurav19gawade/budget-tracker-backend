package com.budgettracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// @Data is intentionally NOT used here. @Data generates equals/hashCode using all
// fields including the OneToMany collections. With bidirectional relationships this
// causes infinite recursion: User.hashCode() → Category.hashCode() → User.hashCode()…
// Instead we use @Getter/@Setter and define equals/hashCode only on the id.
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @Column(unique = true, nullable = false)
    @ToString.Include
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @ToString.Include
    private String name;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Collections excluded from equals/hashCode/toString — including them causes:
    //   1. Infinite recursion (bidirectional nav back to User)
    //   2. Unintended lazy-load during equals/hashCode in a detached context
    //   3. StackOverflowError when Lombok generates toString on a loaded graph
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<TellerEnrollment> tellerEnrollments = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<Budget> budgets = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<Transaction> transactions = new HashSet<>();
}