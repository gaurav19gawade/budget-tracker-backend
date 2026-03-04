package com.budgettracker.controller;

import com.budgettracker.dto.AuthRequest;
import com.budgettracker.dto.AuthResponse;
import com.budgettracker.dto.RegisterRequest;
import com.budgettracker.security.JwtTokenProvider;
import com.budgettracker.security.UserPrincipal;
import com.budgettracker.service.AuthService;
import com.budgettracker.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider tokenProvider;

    @Value("${jwt.cookie-name:auth_token}")
    private String accessCookieName;

    @Value("${jwt.refresh-cookie-name:refresh_token}")
    private String refreshCookieName;

    @Value("${jwt.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${jwt.expiration:900000}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-expiration:2592000000}")
    private long refreshExpirationMs;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.register(request);
        setAuthCookies(response, authResponse.getToken(),
                refreshTokenService.createRefreshToken(authResponse.getUserId()));
        return ResponseEntity.ok(stripToken(authResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.login(request);
        setAuthCookies(response, authResponse.getToken(),
                refreshTokenService.createRefreshToken(authResponse.getUserId()));
        return ResponseEntity.ok(stripToken(authResponse));
    }

    /**
     * Silent token refresh — called automatically by the axios interceptor when
     * any request returns 401. Reads the refresh cookie, validates it, rotates it
     * (issues a new refresh token), and sets a fresh access token cookie.
     * The frontend retries the original request transparently.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String rawRefreshToken = extractCookie(request, refreshCookieName);
        if (rawRefreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token");
        }

        try {
            // validateAndRotate revokes the old token and returns the userId
            Long userId = refreshTokenService.validateAndRotate(rawRefreshToken);

            // Issue new access token
            String newAccessToken = tokenProvider.generateTokenFromUserId(userId);

            // Issue new refresh token (rotation — each refresh token is single-use)
            String newRefreshToken = refreshTokenService.createRefreshToken(userId);

            setAuthCookies(response, newAccessToken, newRefreshToken);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            // Clear cookies so the browser doesn't keep sending a bad refresh token
            clearAuthCookies(response);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh failed");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletResponse response) {

        if (currentUser != null) {
            refreshTokenService.revokeAll(currentUser.getId());
        }
        clearAuthCookies(response);
        return ResponseEntity.ok().build();
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response,
                                String accessToken, String refreshToken) {
        response.addHeader("Set-Cookie", buildCookie(
                accessCookieName, accessToken,
                (int) (accessExpirationMs / 1000)));

        response.addHeader("Set-Cookie", buildCookie(
                refreshCookieName, refreshToken,
                (int) (refreshExpirationMs / 1000)));
    }

    private void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie(accessCookieName, "", 0));
        response.addHeader("Set-Cookie", buildCookie(refreshCookieName, "", 0));
    }

    private String buildCookie(String name, String value, int maxAge) {
        return String.format(
                "%s=%s; Path=/; HttpOnly; %sSameSite=None; Max-Age=%d",
                name, value,
                cookieSecure ? "Secure; " : "",
                maxAge
        );
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /** Remove token from response body — it lives in the cookie only. */
    private AuthResponse stripToken(AuthResponse response) {
        response.setToken(null);
        return response;
    }
}