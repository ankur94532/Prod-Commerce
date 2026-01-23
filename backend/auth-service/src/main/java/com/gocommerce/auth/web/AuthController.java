package com.gocommerce.auth.web;

import com.gocommerce.auth.dto.AuthResponse;
import com.gocommerce.auth.dto.LoginRequest;
import com.gocommerce.auth.dto.RegisterRequest;
import com.gocommerce.auth.service.AuthService;
import com.gocommerce.auth.metrics.AuthMetrics;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthMetrics authMetrics;

    public AuthController(AuthService authService, AuthMetrics authMetrics) {
        this.authService = authService;
        this.authMetrics = authMetrics;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        // count successful registrations
        authMetrics.onRegistration();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("data", response));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        authMetrics.onLoginAttempt();
        try {
            AuthResponse response = authService.login(request);
            authMetrics.onLoginSuccess();
            return ResponseEntity.ok(Map.of("data", response));
        } catch (IllegalArgumentException ex) {
            // same error behaviour as before, just add a metric
            authMetrics.onLoginFailure();
            throw ex;
        }
    }
}
