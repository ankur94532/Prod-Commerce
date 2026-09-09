package com.gocommerce.platform.security;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared JWT configuration. Bound from {@code security.jwt.*} by whichever service
 * registers it; the secret is never logged and never exposed by a getter.
 */
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private static final int MINIMUM_SECRET_LENGTH = 32;

    private String secret;
    private String activeKeyId;
    private String privateKeyBase64;
    private String publicKeyBase64;
    private String previousKeyId;
    private String previousPublicKeyBase64;
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public SecretKey getSigningKey() {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "security.jwt.secret must be configured and at least " + MINIMUM_SECRET_LENGTH + " characters long");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns the active RSA signing key. Only the auth service should call this. */
    public PrivateKey getPrivateKey() {
        requireActiveRsaConfiguration(true);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(decodeKey(privateKeyBase64)));
        } catch (Exception error) {
            throw new IllegalStateException("security.jwt.private-key-base64 is not a valid PKCS#8 RSA key", error);
        }
    }

    /** Active and overlap-window verification keys, keyed by the JWT {@code kid}. */
    public Map<String, PublicKey> getPublicKeys() {
        requireActiveRsaConfiguration(false);
        Map<String, PublicKey> keys = new LinkedHashMap<>();
        keys.put(activeKeyId, parsePublicKey(publicKeyBase64, "security.jwt.public-key-base64"));
        boolean hasPreviousId = hasText(previousKeyId);
        boolean hasPreviousKey = hasText(previousPublicKeyBase64);
        if (hasPreviousId != hasPreviousKey) {
            throw new IllegalStateException(
                    "security.jwt.previous-key-id and previous-public-key-base64 must be configured together");
        }
        if (hasPreviousId) {
            if (activeKeyId.equals(previousKeyId)) {
                throw new IllegalStateException("Active and previous JWT key IDs must be different");
            }
            keys.put(previousKeyId,
                    parsePublicKey(previousPublicKeyBase64, "security.jwt.previous-public-key-base64"));
        }
        return Map.copyOf(keys);
    }

    public Key getVerificationKey(String keyId) {
        if (hasText(keyId)) {
            PublicKey key = getPublicKeys().get(keyId);
            if (key == null) {
                throw new IllegalArgumentException("Unknown JWT key ID");
            }
            return key;
        }
        // Tokens issued before asymmetric-key rollout have no kid. Keep this narrow
        // compatibility path only while the legacy secret is explicitly configured.
        if (hasLegacySecret()) {
            return getSigningKey();
        }
        throw new IllegalArgumentException("JWT key ID is missing");
    }

    public boolean isRsaConfigured() {
        return hasText(activeKeyId) || hasText(privateKeyBase64) || hasText(publicKeyBase64)
                || hasText(previousKeyId) || hasText(previousPublicKeyBase64);
    }

    public boolean hasLegacySecret() {
        return hasText(secret);
    }

    public void validateForVerifier() {
        if (isRsaConfigured()) {
            for (PublicKey key : getPublicKeys().values()) {
                if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) {
                    throw new IllegalStateException("JWT RSA public keys must be at least 2048 bits");
                }
            }
        } else {
            getSigningKey();
        }
    }

    public void validateForIssuer() {
        validateForVerifier();
        if (!isRsaConfigured()) {
            return;
        }
        PrivateKey privateKey = getPrivateKey();
        PublicKey publicKey = getPublicKeys().get(activeKeyId);
        if (!(privateKey instanceof RSAPrivateKey privateRsa)
                || !(publicKey instanceof RSAPublicKey publicRsa)
                || !privateRsa.getModulus().equals(publicRsa.getModulus())) {
            throw new IllegalStateException("Active JWT private and public keys do not form a pair");
        }
    }

    private void requireActiveRsaConfiguration(boolean privateKeyRequired) {
        if (!hasText(activeKeyId) || !hasText(publicKeyBase64)
                || (privateKeyRequired && !hasText(privateKeyBase64))) {
            throw new IllegalStateException("RSA JWT configuration requires active-key-id, public-key-base64"
                    + (privateKeyRequired ? ", and private-key-base64" : ""));
        }
    }

    private static PublicKey parsePublicKey(String encoded, String property) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodeKey(encoded)));
        } catch (Exception error) {
            throw new IllegalStateException(property + " is not a valid X.509 RSA key", error);
        }
    }

    private static byte[] decodeKey(String encoded) {
        String normalized = encoded
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public void setPrivateKeyBase64(String privateKeyBase64) {
        this.privateKeyBase64 = privateKeyBase64;
    }

    public void setPublicKeyBase64(String publicKeyBase64) {
        this.publicKeyBase64 = publicKeyBase64;
    }

    public String getPreviousKeyId() {
        return previousKeyId;
    }

    public void setPreviousKeyId(String previousKeyId) {
        this.previousKeyId = previousKeyId;
    }

    public void setPreviousPublicKeyBase64(String previousPublicKeyBase64) {
        this.previousPublicKeyBase64 = previousPublicKeyBase64;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }
}
