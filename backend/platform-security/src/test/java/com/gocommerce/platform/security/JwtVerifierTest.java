package com.gocommerce.platform.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    private static final String SECRET = "unit-test-secret-value-of-at-least-32-chars";

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        return properties;
    }

    private final JwtProperties properties = properties(SECRET);
    private final JwtIssuer issuer = new JwtIssuer(properties);
    private final JwtVerifier verifier = new JwtVerifier(properties);

    @Test
    void accessTokenRoundTripsSubjectProfileAndRole() {
        String token = issuer.accessToken("user-1", "a@example.com", "Ada", "ADMIN");

        VerifiedToken verified = verifier.verify(token, TokenType.ACCESS);

        assertThat(verified.subject()).isEqualTo("user-1");
        assertThat(verified.email()).isEqualTo("a@example.com");
        assertThat(verified.fullName()).isEqualTo("Ada");
        assertThat(verified.role()).isEqualTo("ADMIN");
        assertThat(verified.authorities()).containsExactly("ROLE_ADMIN");
        assertThat(verified.principal()).isEqualTo(new AuthenticatedUser("user-1", "a@example.com", "Ada", "ADMIN"));
    }

    @Test
    void refreshTokenCannotBeUsedAsAnAccessToken() {
        String refresh = issuer.refreshToken("user-1");

        assertThat(verifier.verify(refresh, TokenType.REFRESH).subject()).isEqualTo("user-1");
        assertThatThrownBy(() -> verifier.verify(refresh, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Expected a access token, got refresh");
    }

    @Test
    void accessTokenCannotBeRedeemedForRefresh() {
        String access = issuer.accessToken("user-1", null, null, "USER");

        assertThatThrownBy(() -> verifier.verify(access, TokenType.REFRESH))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("got access");
    }

    @Test
    void refreshTokenCarriesNoProfileOrRoleClaims() {
        VerifiedToken verified = verifier.verify(issuer.refreshToken("user-1"), TokenType.REFRESH);

        assertThat(verified.email()).isNull();
        assertThat(verified.role()).isNull();
        assertThat(verified.roles()).isEmpty();
        assertThat(verified.authorities()).isEmpty();
    }

    @Test
    void tokenWithoutTypeClaimIsRejected() {
        String legacy = Jwts.builder()
                .setSubject("user-1")
                .addClaims(Map.of("role", "USER"))
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(properties.getSigningKey())
                .compact();

        assertThatThrownBy(() -> verifier.verify(legacy, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("no 'type' claim");
    }

    @Test
    void tokenWithUnknownTypeIsRejected() {
        String odd = Jwts.builder()
                .setSubject("user-1")
                .addClaims(Map.of(TokenType.CLAIM, "session"))
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(properties.getSigningKey())
                .compact();

        assertThatThrownBy(() -> verifier.verify(odd, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Unknown token type");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtIssuer attacker = new JwtIssuer(properties("a-completely-different-secret-of-32-chars"));

        assertThatThrownBy(() -> verifier.verify(attacker.accessToken("user-1", null, null, "ADMIN"), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Token rejected");
    }

    @Test
    void unsignedTokenIsRejected() {
        String unsigned = Jwts.builder()
                .setSubject("user-1")
                .addClaims(Map.of(TokenType.CLAIM, "access", "role", "ADMIN"))
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .compact();

        assertThatThrownBy(() -> verifier.verify(unsigned, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtProperties shortLived = properties(SECRET);
        shortLived.setAccessTokenTtl(Duration.ofSeconds(-1));

        assertThatThrownBy(() -> verifier.verify(
                new JwtIssuer(shortLived).accessToken("user-1", null, null, "USER"), TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("ExpiredJwtException");
    }

    @Test
    void garbageAndEmptyTokensAreRejected() {
        for (String token : List.of("", "   ", "not.a.jwt", "aaaa")) {
            assertThatThrownBy(() -> verifier.verify(token, TokenType.ACCESS))
                    .isInstanceOf(InvalidTokenException.class);
        }
        assertThatThrownBy(() -> verifier.verify(null, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectionMessagesNeverEchoTheToken() {
        String token = "not.a.jwt";

        assertThatThrownBy(() -> verifier.verify(token, TokenType.ACCESS))
                .isInstanceOf(InvalidTokenException.class)
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(token));
    }

    @Test
    void rolesClaimIsMergedWithTheSingularRoleClaim() {
        String token = Jwts.builder()
                .setSubject("user-1")
                .addClaims(Map.of(TokenType.CLAIM, "access", "role", "USER", "roles", List.of("ADMIN", "ROLE_SUPPORT")))
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(properties.getSigningKey())
                .compact();

        VerifiedToken verified = verifier.verify(token, TokenType.ACCESS);

        assertThat(verified.roles()).containsExactlyInAnyOrder("USER", "ADMIN", "ROLE_SUPPORT");
        assertThat(verified.authorities()).containsExactly("ROLE_ADMIN", "ROLE_SUPPORT", "ROLE_USER");
        assertThat(verified.role()).isEqualTo("USER");
    }

    @Test
    void bearerHeaderParsingAcceptsOnlyABearerScheme() {
        assertThat(JwtVerifier.bearerToken("Bearer abc")).isEqualTo("abc");
        assertThat(JwtVerifier.bearerToken("Bearer  abc ")).isEqualTo("abc");
        assertThat(JwtVerifier.bearerToken("Bearer ")).isNull();
        assertThat(JwtVerifier.bearerToken("Basic abc")).isNull();
        assertThat(JwtVerifier.bearerToken("bearer abc")).isNull();
        assertThat(JwtVerifier.bearerToken(null)).isNull();
    }

    @Test
    void shortOrMissingSecretsRefuseToProduceAKey() {
        assertThatThrownBy(() -> properties("too-short").getSigningKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
        assertThatThrownBy(() -> new JwtProperties().getSigningKey())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void signingKeyMatchesAPlainHmacKeyBuiltFromTheSameSecret() {
        assertThat(properties.getSigningKey().getEncoded())
                .isEqualTo(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)).getEncoded());
    }
}
