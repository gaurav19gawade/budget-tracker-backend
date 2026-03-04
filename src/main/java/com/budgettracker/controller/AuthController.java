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

import java.util.Arrays;
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

    // Whether to set Secure flag — true in prod (HTTPS), false in local dev (HTTP)
    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.register(request);
        setAuthCookie(response, authResponse.getToken());

        // Return user info but strip the token from the JSON body
        return ResponseEntity.ok(stripToken(authResponse));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.login(request);
        setAuthCookie(response, authResponse.getToken());

        // Return user info but strip the token from the JSON body
        return ResponseEntity.ok(stripToken(authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        clearAuthCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> checkAuth(HttpServletRequest request) {
        // If this endpoint is reached, the JwtAuthenticationFilter already validated
        // the cookie, so the user is authenticated. Useful for frontend session checks.
        return ResponseEntity.ok(Map.of("status", "authenticated"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(cookieName, token);
        cookie.setHttpOnly(true);              // Not accessible via JS — blocks XSS token theft
        cookie.setSecure(cookieSecure);        // HTTPS only in prod; false for local dev over HTTP
        cookie.setPath("/");                   // Sent on all API requests
        cookie.setMaxAge((int) (jwtExpirationMs / 1000)); // Match JWT lifetime

        // SameSite=Strict prevents CSRF — cookie not sent on cross-site requests.
        // We set this via the header directly because the Servlet Cookie API
        // doesn't expose SameSite until Servlet 6.1 / Spring Boot 3.3+.
        response.addCookie(cookie);
        response.addHeader("Set-Cookie",
                String.format("%s=%s; Path=/; HttpOnly; %sSameSite=Strict; Max-Age=%d",
                        cookieName, token,
                        cookieSecure ? "Secure; " : "",
                        (int) (jwtExpirationMs / 1000)));
    }

    private void clearAuthCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Expire immediately
        response.addCookie(cookie);
        response.addHeader("Set-Cookie",
                String.format("%s=; Path=/; HttpOnly; %sSameSite=Strict; Max-Age=0",
                        cookieName,
                        cookieSecure ? "Secure; " : ""));
    }

    /**
     * Strip the token from the JSON response body.
     * The token is now exclusively in the httpOnly cookie — there's no reason
     * to expose it to JavaScript via the response body.
     */
    private AuthResponse stripToken(AuthResponse original) {
        return AuthResponse.builder()
                .token(null)   // Intentionally null — token lives in cookie only
                .userId(original.getUserId())
                .email(original.getEmail())
                .name(original.getName())
                .build();
    }
}