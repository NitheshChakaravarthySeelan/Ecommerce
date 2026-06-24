package com.ecommerce.auth.model;

/**
 * Public user model returned by /auth/me.
 *
 * <p>Unlike UserEntity, this does not expose the password hash.
 */
public record User(String id, String email, String role) {
}
