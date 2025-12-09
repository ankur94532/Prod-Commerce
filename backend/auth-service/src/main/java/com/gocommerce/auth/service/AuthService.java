package com.gocommerce.auth.service;

import com.gocommerce.auth.dto.AuthResponse;
import com.gocommerce.auth.dto.LoginRequest;
import com.gocommerce.auth.dto.RegisterRequest;
import com.gocommerce.auth.dto.UserResponse;
import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.repository.UserRepository;
import com.gocommerce.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                "customer");

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
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        AuthResponse.TokenPair tokenPair = new AuthResponse.TokenPair();
        tokenPair.setAccessToken(accessToken);
        tokenPair.setRefreshToken(refreshToken);

        UserResponse userResp = new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCreatedAt());

        AuthResponse resp = new AuthResponse();
        resp.setUser(userResp);
        resp.setTokens(tokenPair);
        return resp;
    }
}
