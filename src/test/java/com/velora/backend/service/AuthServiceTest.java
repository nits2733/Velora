package com.velora.backend.service;

import com.velora.backend.config.JwtProperties;
import com.velora.backend.dto.auth.AuthResponse;
import com.velora.backend.dto.auth.RegisterRequest;
import com.velora.backend.entity.Role;
import com.velora.backend.entity.User;
import com.velora.backend.exception.DuplicateResourceException;
import com.velora.backend.repository.ProfessionalProfileRepository;
import com.velora.backend.repository.UserRepository;
import com.velora.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfessionalProfileRepository professionalProfileRepository;
    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-at-least-32-bytes-long!!");
        properties.setExpirationMs(1000 * 60 * 60);
        properties.setIssuer("velora-test");
        JwtService jwtService = new JwtService(properties);

        authService = new AuthService(userRepository, professionalProfileRepository,
                new BCryptPasswordEncoder(), jwtService, authenticationManager);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@velora.test")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("taken@velora.test", "password1", "Someone", null, Role.CUSTOMER);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void registerCustomerDoesNotCreateProfessionalProfile() {
        when(userRepository.existsByEmail("new@velora.test")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        RegisterRequest request = new RegisterRequest("new@velora.test", "password1", "New Customer", null, Role.CUSTOMER);

        AuthResponse response = authService.register(request);

        assertThat(response.user().id()).isEqualTo(42L);
        assertThat(response.user().role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.accessToken()).isNotBlank();
        org.mockito.Mockito.verifyNoInteractions(professionalProfileRepository);
    }

    @Test
    void registerProfessionalCreatesProfessionalProfile() {
        when(userRepository.existsByEmail("professional@velora.test")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        RegisterRequest request = new RegisterRequest("professional@velora.test", "password1", "New Professional", null, Role.PROFESSIONAL);

        authService.register(request);

        org.mockito.Mockito.verify(professionalProfileRepository).save(any());
    }

    @Test
    void registerRejectsAdminRole() {
        RegisterRequest request = new RegisterRequest("wannabe.admin@velora.test", "password1", "Someone", null, Role.ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot self-register as an admin");

        org.mockito.Mockito.verifyNoInteractions(userRepository);
    }
}
