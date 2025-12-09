package com.gocommerce.auth.dto;

public class AuthResponse {

    private UserResponse user;
    private TokenPair tokens;

    public static class TokenPair {
        private String accessToken;
        private String refreshToken;

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public UserResponse getUser() {
        return user;
    }

    public TokenPair getTokens() {
        return tokens;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }

    public void setTokens(TokenPair tokens) {
        this.tokens = tokens;
    }
}
