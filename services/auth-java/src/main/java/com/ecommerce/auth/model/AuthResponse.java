package com.ecommerce.auth.model;

public record AuthResponse(String userId, String accessToken, String refreshToken) {
}
