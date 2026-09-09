package com.gocommerce.auth.service;

import com.gocommerce.auth.dto.AuthResponse;
import com.gocommerce.auth.dto.LoginRequest;
import com.gocommerce.auth.dto.RegisterRequest;
import com.gocommerce.auth.dto.UserResponse;
import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import com.gocommerce.auth.repository.UserRepository;
import com.gocommerce.platform.security.InvalidTokenException;
import com.gocommerce.platform.security.JwtIssuer;
import com.gocommerce.platform.security.JwtVerifier;
import com.gocommerce.platform.security.TokenType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final JwtVerifier jwtVerifier;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtIssuer jwtIssuer,
            JwtVerifier jwtVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.jwtVerifier = jwtVerifier;
    }

    /**
     * Exchanges a refresh token for a new token pair. The refresh token is the only token
     * accepted here, and an access token presented in its place is rejected.
     */
    public AuthResponse refresh(String refreshToken) {
        String userId;
        try {
            userId = jwtVerifier.verify(refreshToken, TokenType.REFRESH).subject();
        } catch (InvalidTokenException error) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        return buildAuthResponse(user);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                Role.USER);

        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtIssuer.accessToken(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
        String refreshToken = jwtIssuer.refreshToken(user.getId());

        AuthResponse.TokenPair tokenPair = new AuthResponse.TokenPair();
        tokenPair.setAccessToken(accessToken);
        tokenPair.setRefreshToken(refreshToken);

        UserResponse userResp = new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getCreatedAt());

        AuthResponse resp = new AuthResponse();
        resp.setUser(userResp);
        resp.setTokens(tokenPair);
        return resp;
    }
}
