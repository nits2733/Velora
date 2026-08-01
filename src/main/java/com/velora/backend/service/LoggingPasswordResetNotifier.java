package com.velora.backend.service;

import com.velora.backend.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Stand-in delivery until an email provider is wired up: writes the token to the
 * application log so the flow is exercisable end to end in development.
 * <p>
 * Backs off automatically the moment a real {@link PasswordResetNotifier} bean exists.
 * This must not be what runs in production - a reset token in a log file is a reset token
 * available to anyone who can read logs.
 */
@Component
@ConditionalOnMissingBean(ignored = LoggingPasswordResetNotifier.class, value = PasswordResetNotifier.class)
@Slf4j
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    @Override
    public void sendResetToken(User user, String rawToken) {
        log.warn("No email delivery configured - password reset token for {} is: {}",
                user.getEmail(), rawToken);
    }
}
