package com.velora.backend.dto.auth;

import com.velora.backend.entity.Role;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String email,
        String fullName,
        Role role
) {
    public static AuthResponse of(String token, Long userId, String email, String fullName, Role role) {
        return new AuthResponse(token, "Bearer", userId, email, fullName, role);
    }
}
