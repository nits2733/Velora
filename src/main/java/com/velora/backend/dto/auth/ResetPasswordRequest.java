package com.velora.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Same password rules as registration - they're duplicated nowhere else, so they stay in step. */
public record ResetPasswordRequest(
        @NotBlank String token,

        @NotBlank @Size(min = 8, max = 100,
                message = "Password must be between 8 and 100 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one digit")
        String newPassword
) {
}
