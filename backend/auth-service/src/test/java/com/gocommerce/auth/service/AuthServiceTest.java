package com.gocommerce.auth.service;

import com.gocommerce.auth.dto.AuthResponse;
import com.gocommerce.auth.dto.LoginRequest;
import com.gocommerce.auth.dto.RegisterRequest;
import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import com.gocommerce.auth.repository.UserRepository;
import com.gocommerce.auth.security.JwtProperties;
import com.gocommerce.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

        @Mock
        private UserRepository userRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        // use real JwtService (no mock)
        private JwtService jwtService;

        private AuthService authService;

        private RegisterRequest registerRequest;
        private LoginRequest loginRequest;

        @BeforeEach
        void setUp() {
                // Real JwtService with test config
                JwtProperties props = new JwtProperties();
                props.setSecret("test_secret_very_long_1234567890");
                props.setAccessTokenTtlMinutes(60);
                props.setRefreshTokenTtlDays(30);
                jwtService = new JwtService(props);

                // AuthService with mocks + real JwtService
                authService = new AuthService(userRepository, passwordEncoder, jwtService);

                registerRequest = new RegisterRequest();
                registerRequest.setEmail("user1@example.com");
                registerRequest.setPassword("password123");
                registerRequest.setFullName("User One");

                loginRequest = new LoginRequest();
                loginRequest.setEmail("user1@example.com");
                loginRequest.setPassword("password123");
        }

        @Test
        void register_createsUserAndReturnsTokens() {
                // arrange
                when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
                when(passwordEncoder.encode("password123")).thenReturn("encoded_pw");

                User savedUser = new User(
                                registerRequest.getEmail(),
                                "encoded_pw",
                                registerRequest.getFullName(),
                                Role.USER);
                when(userRepository.save(any(User.class))).thenReturn(savedUser);

                // act
                AuthResponse response = authService.register(registerRequest);

                // assert
                assertNotNull(response);
                assertNotNull(response.getUser());
                assertNotNull(response.getTokens());
                assertNotNull(response.getTokens().getAccessToken());
                assertNotNull(response.getTokens().getRefreshToken());

                assertEquals(registerRequest.getEmail(), response.getUser().getEmail());
                assertEquals("User One", response.getUser().getFullName());
                assertEquals("USER", response.getUser().getRole());

                // verify that userRepository.save was called with encoded password
                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                verify(userRepository).save(userCaptor.capture());
                User passedUser = userCaptor.getValue();
                assertEquals("user1@example.com", passedUser.getEmail());
                assertEquals("encoded_pw", passedUser.getPasswordHash());
                assertEquals(Role.USER, passedUser.getRole());
        }

        @Test
        void register_throwsIfEmailAlreadyExists() {
                when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.register(registerRequest));

                assertEquals("Email already in use", ex.getMessage());
                verify(userRepository, never()).save(any());
        }

        @Test
        void login_returnsTokensOnSuccess() {
                // arrange
                User existingUser = new User(
                                loginRequest.getEmail(),
                                "encoded_pw",
                                "User One",
                                Role.USER);

                when(userRepository.findByEmail(loginRequest.getEmail()))
                                .thenReturn(Optional.of(existingUser));
                when(passwordEncoder.matches("password123", "encoded_pw")).thenReturn(true);

                // act
                AuthResponse response = authService.login(loginRequest);

                // assert
                assertNotNull(response);
                assertNotNull(response.getTokens());
                assertNotNull(response.getTokens().getAccessToken());
                assertNotNull(response.getTokens().getRefreshToken());
                assertEquals("user1@example.com", response.getUser().getEmail());
                assertEquals("USER", response.getUser().getRole());

                verify(userRepository).findByEmail("user1@example.com");
                verify(passwordEncoder).matches("password123", "encoded_pw");
        }

        @Test
        void login_throwsOnInvalidPassword() {
                User existingUser = new User(
                                loginRequest.getEmail(),
                                "encoded_pw",
                                "User One",
                                Role.USER);

                when(userRepository.findByEmail(loginRequest.getEmail()))
                                .thenReturn(Optional.of(existingUser));
                when(passwordEncoder.matches("password123", "encoded_pw")).thenReturn(false);

                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.login(loginRequest));

                assertEquals("Invalid credentials", ex.getMessage());
        }

        @Test
        void login_throwsWhenUserNotFound() {
                when(userRepository.findByEmail(loginRequest.getEmail()))
                                .thenReturn(Optional.empty());

                IllegalArgumentException ex = assertThrows(
                                IllegalArgumentException.class,
                                () -> authService.login(loginRequest));

                assertEquals("Invalid credentials", ex.getMessage());
                verify(passwordEncoder, never()).matches(any(), any());
        }
}
