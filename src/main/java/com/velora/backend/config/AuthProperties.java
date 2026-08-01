package com.velora.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lifetimes for the two opaque (non-JWT) tokens. Kept out of {@link JwtProperties}
 * deliberately - neither of these is signed or self-describing; they're random strings
 * whose authority comes entirely from a matching database row.
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * How long a refresh token stays valid. Long by design: this is what keeps a phone
     * logged in for weeks while access tokens stay short-lived.
     */
    private Duration refreshTokenTtl = Duration.ofDays(30);

    /** Short by design - a recovery grant sitting in an inbox is a standing risk. */
    private Duration passwordResetTtl = Duration.ofMinutes(30);

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Duration getPasswordResetTtl() {
        return passwordResetTtl;
    }

    public void setPasswordResetTtl(Duration passwordResetTtl) {
        this.passwordResetTtl = passwordResetTtl;
    }
}
