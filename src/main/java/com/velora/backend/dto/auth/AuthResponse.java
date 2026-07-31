package com.velora.backend.dto.auth;

import com.velora.backend.entity.Role;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UserSummary user
) {
    public record UserSummary(
            Long id,
            String email,
            String fullName,
            Role role
    ) {
    }

    public static AuthResponse of(String token, Long userId, String email, String fullName, Role role) {
        return new AuthResponse(token, "Bearer", new UserSummary(userId, email, fullName, role));
    }
}
