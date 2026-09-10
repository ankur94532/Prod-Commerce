package com.gocommerce.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolverAdapter;

import java.security.Key;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Verifies signature, expiry, and token type. This is the only place any service decides
 * whether a bearer token is real: the gateway used to accept any string beginning with
 * "Bearer ", which meant the edge performed no authentication at all.
 */
public class JwtVerifier {

    private final JwtProperties properties;

    public JwtVerifier(JwtProperties properties) {
        this.properties = properties;
        properties.validateForVerifier();
    }

    public VerifiedToken verify(String token, TokenType expected) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token is missing");
        }
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKeyResolver(new SigningKeyResolverAdapter() {
                        // JwsHeader is a generic type, but jjwt 0.11.5 declares this callback
                        // with the raw one, so the raw type here is required rather than
                        // sloppy. Narrowing it to JwsHeader<?> compiles and is NOT an
                        // override -- it becomes an overload, jjwt keeps calling the adapter's
                        // raw method, that returns null, and every token fails to verify.
                        // Fifteen tests caught exactly that; the warning is the safer option.
                        @SuppressWarnings("rawtypes")
                        @Override
                        public Key resolveSigningKey(JwsHeader header, Claims untrustedClaims) {
                            return properties.resolveVerificationKey(header.getKeyId());
                        }
                    })
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException error) {
            // Never echo the token itself; the message is what reaches logs.
            throw new InvalidTokenException("Token rejected: " + error.getClass().getSimpleName(), error);
        }

        String declared = claims.get(TokenType.CLAIM, String.class);
        if (declared == null) {
            throw new InvalidTokenException(
                    "Token has no '" + TokenType.CLAIM + "' claim; access and refresh tokens must be distinguishable");
        }
        TokenType actual = TokenType.fromClaim(declared);
        if (actual != expected) {
            throw new InvalidTokenException("Expected a " + expected.claimValue() + " token, got " + actual.claimValue());
        }

        String role = claims.get("role", String.class);
        Set<String> roles = new LinkedHashSet<>();
        if (role != null && !role.isBlank()) {
            roles.add(role);
        }
        if (claims.get("roles") instanceof Collection<?> declaredRoles) {
            for (Object value : declaredRoles) {
                if (value != null && !value.toString().isBlank()) {
                    roles.add(value.toString());
                }
            }
        }
        return new VerifiedToken(claims.getSubject(), claims.get("email", String.class),
                claims.get("fullName", String.class), role, Set.copyOf(roles), actual);
    }

    /** Extracts a bearer token from an Authorization header value, or null when absent. */
    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }
}
