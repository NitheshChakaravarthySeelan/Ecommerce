package com.ecommerce.auth.model;

/**
 * Request payload for /auth/register and /auth/login.
 */
public record AuthRequest(String email, String password) {
}
