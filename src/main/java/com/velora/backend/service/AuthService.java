package com.velora.backend.service;

import com.velora.backend.dto.auth.AuthResponse;
import com.velora.backend.dto.auth.LoginRequest;
import com.velora.backend.dto.auth.RegisterRequest;
import com.velora.backend.entity.DesignerProfile;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.DuplicateResourceException;
import com.velora.backend.repository.DesignerProfileRepository;
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
    private final DesignerProfileRepository designerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

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

        if (user.getRole() == Role.DESIGNER) {
            DesignerProfile profile = DesignerProfile.builder()
                    .user(user)
                    .build();
            designerProfileRepository.save(profile);
        }

        UserPrincipal principal = UserPrincipal.fromEntity(user);
        String token = jwtService.generateToken(principal);

        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("User authenticated but not found: " + normalizedEmail));

        UserPrincipal principal = UserPrincipal.fromEntity(user);
        String token = jwtService.generateToken(principal);

        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
