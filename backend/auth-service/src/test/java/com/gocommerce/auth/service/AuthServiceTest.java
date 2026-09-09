package com.gocommerce.auth.service;

import com.gocommerce.auth.dto.AuthResponse;
import com.gocommerce.auth.dto.LoginRequest;
import com.gocommerce.auth.dto.RegisterRequest;
import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import com.gocommerce.auth.repository.UserRepository;
import com.gocommerce.platform.security.InvalidTokenException;
import com.gocommerce.platform.security.JwtIssuer;
import com.gocommerce.platform.security.JwtProperties;
import com.gocommerce.platform.security.JwtVerifier;
import com.gocommerce.platform.security.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

        @Mock
        private UserRepository userRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        // Real issuer and verifier: token typing is part of what these tests check.
        private JwtIssuer jwtIssuer;
        private JwtVerifier jwtVerifier;

        private AuthService authService;

        private RegisterRequest registerRequest;
        private LoginRequest loginRequest;

        @BeforeEach
        void setUp() {
                JwtProperties props = new JwtProperties();
                props.setSecret("test_secret_very_long_1234567890");
                props.setAccessTokenTtl(Duration.ofMinutes(60));
                props.setRefreshTokenTtl(Duration.ofDays(30));
                jwtIssuer = new JwtIssuer(props);
                jwtVerifier = new JwtVerifier(props);

                authService = new AuthService(userRepository, passwordEncoder, jwtIssuer, jwtVerifier);

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

        private User userWithId(String id) {
                return new User("user1@example.com", "encoded_pw", "User One", Role.USER) {
                        @Override
                        public String getId() {
                                return id;
                        }
                };
        }

        @Test
        void issuedTokensAreTypedAndTheRefreshTokenIsNotAnAccessToken() {
                when(userRepository.findByEmail(loginRequest.getEmail()))
                                .thenReturn(Optional.of(userWithId("user-1")));
                when(passwordEncoder.matches("password123", "encoded_pw")).thenReturn(true);

                AuthResponse response = authService.login(loginRequest);
                String access = response.getTokens().getAccessToken();
                String refresh = response.getTokens().getRefreshToken();

                assertEquals(TokenType.ACCESS, jwtVerifier.verify(access, TokenType.ACCESS).type());
                assertEquals(TokenType.REFRESH, jwtVerifier.verify(refresh, TokenType.REFRESH).type());
                assertThrows(InvalidTokenException.class, () -> jwtVerifier.verify(refresh, TokenType.ACCESS));
                assertNull(jwtVerifier.verify(refresh, TokenType.REFRESH).role());
        }

        @Test
        void refresh_exchangesAValidRefreshTokenForANewPair() {
                User user = userWithId("user-1");
                when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

                AuthResponse response = authService.refresh(jwtIssuer.refreshToken("user-1"));

                assertNotNull(response.getTokens().getAccessToken());
                assertNotNull(response.getTokens().getRefreshToken());
                assertEquals("USER", jwtVerifier.verify(response.getTokens().getAccessToken(), TokenType.ACCESS).role());
        }

        @Test
        void refresh_refusesAnAccessTokenPresentedInPlaceOfARefreshToken() {
                String access = jwtIssuer.accessToken("user-1", "user1@example.com", "User One", "USER");

                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                () -> authService.refresh(access));

                assertEquals("Invalid refresh token", error.getMessage());
                verify(userRepository, never()).findById(any());
        }

        @Test
        void refresh_refusesGarbageAndUnknownUsersIdentically() {
                assertEquals("Invalid refresh token",
                                assertThrows(IllegalArgumentException.class, () -> authService.refresh("nonsense"))
                                                .getMessage());

                when(userRepository.findById("ghost")).thenReturn(Optional.empty());
                assertEquals("Invalid refresh token",
                                assertThrows(IllegalArgumentException.class,
                                                () -> authService.refresh(jwtIssuer.refreshToken("ghost")))
                                                .getMessage());
        }
}
