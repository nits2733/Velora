package com.velora.backend.service;

import com.velora.backend.config.AuthProperties;
import com.velora.backend.entity.PasswordResetToken;
import com.velora.backend.entity.User;
import com.velora.backend.exception.ResourceNotFoundException;
import com.velora.backend.repository.PasswordResetTokenRepository;
import com.velora.backend.repository.UserRepository;
import com.velora.backend.security.SecureTokenGenerator;
import lombok.RequiredArgsConstructor;
import com.velora.backend.exception.AuthenticationFailedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Password recovery and change. Split out of {@link AuthService} because it answers a
 * different question - "prove you may set this password" rather than "prove who you are".
 * <p>
 * Both paths end the same way: every existing session is revoked. A password change is
 * exactly the moment you want any attacker's stolen session to stop working.
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final SecureTokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier passwordResetNotifier;
    private final AuthProperties authProperties;

    /**
     * Starts recovery. Returns normally whether or not the email exists - the response
     * must not reveal which addresses have accounts, the same reason a failed login is
     * vague about which half was wrong. An unknown address simply does nothing.
     */
    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = email.trim().toLowerCase();

        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            // A second request invalidates the first, so only one link is ever live.
            passwordResetTokenRepository.invalidateOutstandingForUser(user.getId(), Instant.now());

            String rawToken = tokenGenerator.generate();
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenGenerator.hash(rawToken))
                    .expiresAt(Instant.now().plus(authProperties.getPasswordResetTtl()))
                    .build());

            passwordResetNotifier.sendResetToken(user, rawToken);
        });
    }

    /**
     * Completes recovery. The token is single-use and is spent even though the password
     * write succeeds - there is no "try again with the same link".
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenGenerator.hash(rawToken))
                .filter(candidate -> candidate.isUsable(Instant.now()))
                .orElseThrow(() -> new AuthenticationFailedException("Invalid or expired password reset token"));

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);

        refreshTokenService.revokeAllForUser(user.getId());
    }

    /**
     * Changes the password of an already-authenticated user. The current password is
     * re-checked here even though the caller holds a valid token: a token proves the
     * session was authenticated at some point, not that the person holding the phone
     * right now knows the password.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(userId);
    }
}
