package com.ecommerce.auth.controller;

import com.ecommerce.auth.model.AuthRequest;
import com.ecommerce.auth.model.AuthResponse;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication endpoints.
 *
 * <p>All endpoints are CORS-enabled for all origins. The API gateway
 * routes /auth/register and /auth/login without JWT authentication;
 * /auth/me requires a Bearer token (validated by the gateway).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /health         — health check</li>
 *   <li>POST /auth/register  — create account, return JWT</li>
 *   <li>POST /auth/login     — authenticate, return JWT</li>
 *   <li>GET  /auth/me        — resolve current user from token</li>
 * </ul>
 */
@CrossOrigin(origins = "*")
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "auth"));
    }

    /**
     * Register a new user account.
     *
     * @param request the registration payload (email, password)
     * @return 200 with JWT on success, 400 with error message on failure
     */
    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request) {
        try {
            String token = authService.register(request.email(), request.password());
            return ResponseEntity.ok(new AuthResponse("", token, ""));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new AuthResponse("", "", e.getMessage()));
        }
    }

    /**
     * Authenticate an existing user.
     *
     * @param request the login payload (email, password)
     * @return 200 with JWT on success, 401 on bad credentials
     */
    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            String token = authService.login(request.email(), request.password());
            return ResponseEntity.ok(new AuthResponse("", token, ""));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(new AuthResponse("", "", e.getMessage()));
        }
    }

    /**
     * Resolve the current user identity from the Authorization header.
     *
     * <p>Returns a guest/anonymous fallback if no valid token is present —
     * this keeps the endpoint safe for unauthenticated calls from the UI.
     *
     * @param authorization the raw Authorization header (optional)
     * @return the resolved User model
     */
    @GetMapping("/auth/me")
    public ResponseEntity<User> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        User user = authService.me(authorization);
        return ResponseEntity.ok(user);
    }
}
