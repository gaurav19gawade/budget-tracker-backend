package com.budgettracker.controller;

import com.budgettracker.dto.AuthRequest;
import com.budgettracker.dto.AuthResponse;
import com.budgettracker.dto.RegisterRequest;
import com.budgettracker.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.cookie-name:auth_token}")
    private String cookieName;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    // true in prod (HTTPS cross-origin), false for local dev (HTTP same-ish origin)
    @Value("${jwt.cookie-secure:true}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.register(request);
        setAuthCookie(response, authResponse.getToken());
        return ResponseEntity.ok(stripToken(authResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.login(request);
        setAuthCookie(response, authResponse.getToken());
        return ResponseEntity.ok(stripToken(authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        clearAuthCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> checkAuth(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of("status", "authenticated"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setAuthCookie(HttpServletResponse response, String token) {
        int maxAgeSeconds = (int) (jwtExpirationMs / 1000);

        // SameSite=None; Secure is required for cross-origin cookies (frontend and
        // backend on different domains, e.g. Railway). The browser will not send
        // SameSite=Strict or SameSite=Lax cookies on cross-origin requests at all.
        //
        // Security trade-off: SameSite=None opens CSRF risk, but we already have
        // csrf().disable() + stateless JWT, so there's no session to hijack.
        // The HttpOnly flag still fully protects against XSS token theft.
        //
        // For local dev (HTTP): cookieSecure=false, SameSite=None still works
        // on localhost in most browsers even without Secure flag.
        String sameSite = "None";

        // Build the Set-Cookie header manually — Servlet Cookie API doesn't
        // expose SameSite until Servlet 6.1 / Spring Boot 3.3+
        String cookieHeader = String.format(
                "%s=%s; Path=/; HttpOnly; %sSameSite=%s; Max-Age=%d",
                cookieName,
                token,
                cookieSecure ? "Secure; " : "",
                sameSite,
                maxAgeSeconds
        );

        // addCookie sets the basic cookie (no SameSite), then we override with
        // the full manual header that includes SameSite=None
        Cookie cookie = new Cookie(cookieName, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);
        response.setHeader("Set-Cookie", cookieHeader); // setHeader (not add) to avoid duplicate
    }

    private void clearAuthCookie(HttpServletResponse response) {
        String cookieHeader = String.format(
                "%s=; Path=/; HttpOnly; %sSameSite=None; Max-Age=0",
                cookieName,
                cookieSecure ? "Secure; " : ""
        );
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        response.setHeader("Set-Cookie", cookieHeader);
    }

    private AuthResponse stripToken(AuthResponse original) {
        return AuthResponse.builder()
                .token(null)
                .userId(original.getUserId())
                .email(original.getEmail())
                .name(original.getName())
                .build();
    }
}