package com.gocommerce.auth.security;

import com.gocommerce.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(user.getId())
                .addClaims(Map.of(
                        "email", user.getEmail(),
                        "role", user.getRole().name(),
                        "fullName", user.getFullName()))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(props.getAccessTokenTtl())))
                .signWith(props.getSigningKey())
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(user.getId())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(props.getRefreshTokenTtl())))
                .signWith(props.getSigningKey())
                .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(props.getSigningKey())
                .build()
                .parseClaimsJws(token);
    }
}
