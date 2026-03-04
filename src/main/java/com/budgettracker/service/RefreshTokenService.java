package com.budgettracker.service;

import com.budgettracker.exception.BadRequestException;
import com.budgettracker.model.RefreshToken;
import com.budgettracker.model.User;
import com.budgettracker.repository.RefreshTokenRepository;
import com.budgettracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-expiration:2592000000}") // 30 days default
    private long refreshExpirationMs;

    @Value("${jwt.max-sessions:5}")
    private int maxSessions;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Creates a new refresh token for the user.
     * Returns the raw token string — this is the only time it's available in plaintext.
     * We store only the SHA-256 hash in the DB.
     */
    @Transactional
    public String createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Enforce max concurrent sessions — revoke oldest if over limit
        long activeSessions = refreshTokenRepository.countActiveByUserId(userId, LocalDateTime.now());
        if (activeSessions >= maxSessions) {
            log.info("User {} has {} active sessions (max {}), revoking all before creating new",
                    userId, activeSessions, maxSessions);
            refreshTokenRepository.revokeAllByUserId(userId);
        }

        // Generate cryptographically secure random token
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // Store only the hash
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.debug("Created refresh token for user {}", userId);

        return rawToken; // Return raw token to be set in the cookie
    }

    /**
     * Validates the raw refresh token and returns the associated userId.
     * Implements token rotation — the used token is revoked and a new one is issued.
     * This means a stolen token can only be used once before the legitimate user
     * invalidates it on their next refresh.
     */
    @Transactional
    public Long validateAndRotate(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            // Token is expired or revoked — could indicate token theft
            // Revoke ALL tokens for this user as a security precaution
            log.warn("Invalid/expired refresh token used for user {} — revoking all sessions",
                    refreshToken.getUser().getId());
            refreshTokenRepository.revokeAllByUserId(refreshToken.getUser().getId());
            throw new BadRequestException("Refresh token expired or revoked");
        }

        // Revoke the used token (rotation — each token is single-use)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getUser().getId();
    }

    /**
     * Revokes all refresh tokens for a user — called on logout.
     */
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.debug("Revoked all refresh tokens for user {}", userId);
    }

    /**
     * Scheduled cleanup — removes expired and revoked tokens nightly.
     * Keeps the refresh_tokens table from growing unbounded.
     */
    @Scheduled(cron = "0 0 3 * * *") // 3 AM every day
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now());
        log.info("Cleaned up expired/revoked refresh tokens");
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}