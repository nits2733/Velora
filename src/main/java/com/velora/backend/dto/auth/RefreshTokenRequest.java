package com.velora.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Used for both refresh and logout - in each case the token itself is the credential. */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
