package com.gocommerce.auth.web;

import com.gocommerce.platform.security.JwtProperties;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwksControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void endpointReturnsCacheablePublicKeySet() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        JwtProperties properties = new JwtProperties();
        properties.setActiveKeyId("current");
        properties.setPublicKeyBase64(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));

        var response = new JwksController(properties).jwks();
        List<Map<String, String>> keys = (List<Map<String, String>>) response.getBody().get("keys");

        assertThat(response.getHeaders().getCacheControl()).contains("max-age=300");
        assertThat(keys).singleElement().satisfies(key -> assertThat(key)
                .containsEntry("kid", "current")
                .containsEntry("kty", "RSA")
                .doesNotContainKey("d"));
    }
}
