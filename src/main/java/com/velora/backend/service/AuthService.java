package com.velora.backend.service;

import com.velora.backend.dto.auth.AuthResponse;
import com.velora.backend.dto.auth.LoginRequest;
import com.velora.backend.dto.auth.RegisterRequest;
import com.velora.backend.entity.ProfessionalProfile;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.DuplicateResourceException;
import com.velora.backend.repository.ProfessionalProfileRepository;
import com.velora.backend.repository.UserRepository;
import com.velora.backend.security.JwtService;
import com.velora.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot self-register as an admin account");
        }

        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phone(request.phone())
                .role(request.role())
                .build();

        user = userRepository.save(user);

        if (user.getRole() == Role.PROFESSIONAL) {
            ProfessionalProfile profile = ProfessionalProfile.builder()
                    .user(user)
                    .build();
            professionalProfileRepository.save(profile);
        }

        return issueSession(user);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("User authenticated but not found: " + normalizedEmail));

        return issueSession(user);
    }

    /**
     * Trades a refresh token for a new pair. The user is reloaded through the refresh
     * token's own row, so a deleted account or a changed role takes effect at the next
     * refresh rather than whenever the access token happens to expire.
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.consumeAndRotate(refreshToken);
        return issueSession(user);
    }

    /** Ends one session. Idempotent: an unknown or already-revoked token is still a success. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /** Ends every session for this user - the "log out on all my devices" case. */
    @Transactional
    public void logoutEverywhere(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    private AuthResponse issueSession(User user) {
        String accessToken = jwtService.generateToken(UserPrincipal.fromEntity(user));
        String refreshToken = refreshTokenService.issue(user);

        return AuthResponse.of(accessToken, refreshToken,
                user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
