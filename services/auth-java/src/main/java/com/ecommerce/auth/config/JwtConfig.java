package com.ecommerce.auth.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT token utility — generates, validates, and parses tokens.
 *
 * <p>Uses HMAC-SHA256 with a configurable secret key and expiration.
 * The secret must be at least 256 bits (32 characters) for HS256.
 *
 * <p><b>Important:</b> The gateway's JWT_SECRET must match this one.
 * If they differ, the gateway will reject tokens issued by this service.
 *
 * <p>Fails at startup if the JWT secret is not configured or is too short.
 */
@Component
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private final String secret;
    private final long expirationMs;
    private SecretKey key;

    public JwtConfig(@Value("${jwt.secret}") String secret,
                     @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            log.error("JWT secret must be at least 32 characters. Set jwt.secret in application.properties or JWT_SECRET env var.");
            throw new IllegalStateException("JWT secret is missing or too short (min 32 chars for HS256)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Create a signed JWT for the given user. Claims: sub=userId, email, role. */
    public String generateToken(String userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** Extract the user ID (subject claim) from a valid token. */
    public String getUserIdFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /** Return true if the token is well-formed and not expired. */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
