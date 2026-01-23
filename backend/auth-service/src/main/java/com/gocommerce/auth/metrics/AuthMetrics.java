package com.gocommerce.auth.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final Counter loginAttempts;
    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter registrations;
    private final Counter tokenRefresh;

    public AuthMetrics(MeterRegistry registry) {
        this.loginAttempts = Counter.builder("auth_login_attempts_total")
                .description("Total login attempts")
                .tag("service", "auth-service")
                .register(registry);

        this.loginSuccess = Counter.builder("auth_login_success_total")
                .description("Successful login attempts")
                .tag("service", "auth-service")
                .register(registry);

        this.loginFailure = Counter.builder("auth_login_failure_total")
                .description("Failed login attempts")
                .tag("service", "auth-service")
                .register(registry);

        this.registrations = Counter.builder("auth_registrations_total")
                .description("User registrations")
                .tag("service", "auth-service")
                .register(registry);

        this.tokenRefresh = Counter.builder("auth_token_refresh_total")
                .description("JWT/refresh-token refresh operations")
                .tag("service", "auth-service")
                .register(registry);
    }

    public void onLoginAttempt() {
        loginAttempts.increment();
    }

    public void onLoginSuccess() {
        loginSuccess.increment();
    }

    public void onLoginFailure() {
        loginFailure.increment();
    }

    public void onRegistration() {
        registrations.increment();
    }

    public void onTokenRefresh() {
        tokenRefresh.increment();
    }
}
