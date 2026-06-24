package com.ecommerce.auth.service;

import com.ecommerce.auth.config.JwtConfig;
import com.ecommerce.auth.entity.UserEntity;
import com.ecommerce.auth.model.User;
import com.ecommerce.auth.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core authentication service handling registration, login, and user
 * identity lookup. All methods are transactional.
 *
 * <p>Passwords are hashed with BCrypt. JWT tokens are generated and
 * validated via {@link JwtConfig}.
 *
 * <p>Data flow:
 * <pre>
 *   Controller → AuthService → UserRepository (JPA) → PostgreSQL
 *                              → JwtConfig (token generation/validation)
 * </pre>
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    public AuthService(UserRepository userRepository, JwtConfig jwtConfig) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtConfig = jwtConfig;
    }

    /**
     * Register a new user account.
     *
     * @param email    the desired email address (must be unique)
     * @param password the raw password (will be BCrypt-hashed)
     * @return a signed JWT containing the user's id, email, and role
     * @throws RuntimeException if the email is already registered
     */
    public String register(String email, String password) {
        if (userRepository.findByEmail(email) != null) {
            throw new RuntimeException("Email already registered");
        }
        UserEntity entity = new UserEntity();
        entity.setEmail(email);
        entity.setPassword(passwordEncoder.encode(password));
        entity.setRole("customer");
        entity = userRepository.save(entity);
        return jwtConfig.generateToken(entity.getId().toString(), entity.getEmail(), entity.getRole());
    }

    /**
     * Authenticate a user and issue a JWT.
     *
     * @param email    the user's email
     * @param password the raw password to verify
     * @return a signed JWT
     * @throws RuntimeException if credentials are invalid
     */
    public String login(String email, String password) {
        UserEntity entity = userRepository.findByEmail(email);
        if (entity == null) {
            throw new RuntimeException("Invalid credentials");
        }
        if (!passwordEncoder.matches(password, entity.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        return jwtConfig.generateToken(entity.getId().toString(), entity.getEmail(), entity.getRole());
    }

    /**
     * Resolve the current user from a Bearer token.
     *
     * <p>If the token is missing, invalid, or the user is not found,
     * a fallback (anonymous/guest) user is returned rather than throwing
     * an exception. This allows unauthenticated access to the gateway's
     * /auth/me endpoint without breaking the UI.
     *
     * @param authorization the raw Authorization header value
     * @return a {@link User} model (never null)
     */
    public User me(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return new User("anonymous", "anonymous@ecommerce.local", "guest");
        }
        String token = authorization.substring(7);
        if (!jwtConfig.validateToken(token)) {
            return new User("anonymous", "anonymous@ecommerce.local", "guest");
        }
        String userId = jwtConfig.getUserIdFromToken(token);
        UserEntity entity = userRepository.findById(Long.parseLong(userId)).orElse(null);
        if (entity != null) {
            return new User(entity.getId().toString(), entity.getEmail(), entity.getRole());
        }
        return new User("user-1", "customer@ecommerce.local", "customer");
    }
}
