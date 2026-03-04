package com.budgettracker.repository;

import com.budgettracker.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Revoke all tokens for a user — called on logout
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    void revokeAllByUserId(@Param("userId") Long userId);

    // Cleanup job — delete expired/revoked tokens older than X days
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff OR r.revoked = true")
    void deleteExpiredAndRevoked(@Param("cutoff") LocalDateTime cutoff);

    // How many active tokens does a user have? Used to enforce a max sessions limit.
    @Query("SELECT COUNT(r) FROM RefreshToken r WHERE r.user.id = :userId AND r.revoked = false AND r.expiresAt > :now")
    long countActiveByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}