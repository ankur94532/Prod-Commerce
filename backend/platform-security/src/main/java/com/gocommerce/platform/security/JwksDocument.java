package com.gocommerce.platform.security;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the public JWKS document for the active key and its rotation overlap key. */
public final class JwksDocument {

    private JwksDocument() {
    }

    public static Map<String, Object> from(JwtProperties properties) {
        List<Map<String, String>> keys = new ArrayList<>();
        properties.resolvePublicKeys().forEach((keyId, key) -> keys.add(toJwk(keyId, key)));
        return Map.of("keys", List.copyOf(keys));
    }

    private static Map<String, String> toJwk(String keyId, PublicKey key) {
        if (!(key instanceof RSAPublicKey rsa)) {
            throw new IllegalStateException("JWKS supports RSA public keys only");
        }
        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", base64Url(rsa.getModulus()));
        jwk.put("e", base64Url(rsa.getPublicExponent()));
        return Map.copyOf(jwk);
    }

    private static String base64Url(BigInteger number) {
        byte[] bytes = number.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        byte[] unsigned = java.util.Arrays.copyOfRange(bytes, offset, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned);
    }
}
