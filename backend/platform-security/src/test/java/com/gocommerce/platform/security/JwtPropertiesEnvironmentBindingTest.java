package com.gocommerce.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Binds JwtProperties the way a deployment does: from environment variables.
 *
 * Every other test here builds JwtProperties directly or from a property map, and all of
 * them passed while auth-service could not start in Kubernetes at all. The environment is
 * not just another property source for this class -- it is the one that produces relaxed
 * names like "security.jwt.private-key.base64", which made Spring's binder treat
 * "security.jwt.private-key" as a nested property, call the resolvePrivateKey() accessor partway
 * through binding, and hit the validation inside before the other fields were set.
 *
 * The accessors are named resolve* now so the binder does not see them as properties. This
 * test fails if anyone renames them back.
 */
class JwtPropertiesEnvironmentBindingTest {

    private static Map<String, Object> environmentWithRsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Base64.Encoder encoder = Base64.getEncoder();

        Map<String, Object> variables = new HashMap<>();
        variables.put("SECURITY_JWT_ACTIVE_KEY_ID", "active-2026");
        variables.put("SECURITY_JWT_PUBLIC_KEY_BASE64", encoder.encodeToString(pair.getPublic().getEncoded()));
        variables.put("SECURITY_JWT_PRIVATE_KEY_BASE64", encoder.encodeToString(pair.getPrivate().getEncoded()));
        variables.put("SECURITY_JWT_PREVIOUS_KEY_ID", "");
        variables.put("SECURITY_JWT_PREVIOUS_PUBLIC_KEY_BASE64", "");
        return variables;
    }

    private static JwtProperties bindFromEnvironment(Map<String, Object> variables) {
        // SystemEnvironmentPropertySource, not a plain map: it is what applies the relaxed
        // name mapping that turns SECURITY_JWT_PRIVATE_KEY_BASE64 into candidate property
        // names, which is the whole point of this test.
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        return Binder.get(environment).bind("security.jwt", Bindable.of(JwtProperties.class)).get();
    }

    @Test
    void bindsAnIssuerConfigurationSuppliedEntirelyThroughEnvironmentVariables() throws Exception {
        Map<String, Object> variables = environmentWithRsaKeys();

        JwtProperties properties = bindFromEnvironment(variables);

        assertThat(properties.getActiveKeyId()).isEqualTo("active-2026");
        // Asserted through behaviour rather than by reading the base64 back: the class has
        // setters but no getters for the key material, and what matters is that both halves
        // bound to a usable, matching pair. validateForIssuer is what auth-service runs on
        // startup, and it is the call that was failing.
        assertThatCode(properties::validateForIssuer).doesNotThrowAnyException();
        assertThat(properties.resolvePublicKeys()).containsOnlyKeys("active-2026");
        assertThat(properties.resolvePrivateKey().getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void bindsAVerifierOnlyConfigurationWithNoPrivateKey() throws Exception {
        // Every service other than auth gets the public half only. This is the shape that
        // kept working while auth-service was broken, so it is worth pinning too.
        Map<String, Object> variables = environmentWithRsaKeys();
        variables.remove("SECURITY_JWT_PRIVATE_KEY_BASE64");

        JwtProperties properties = bindFromEnvironment(variables);

        assertThatCode(properties::validateForVerifier).doesNotThrowAnyException();
        assertThat(properties.resolvePublicKeys()).containsOnlyKeys("active-2026");
    }

    @Test
    void aDerivedAccessorIsNotExposedAsABindableProperty() throws Exception {
        // The defect in one assertion. If resolvePrivateKey() ever comes back, this binds a
        // property that has no business existing and the failure is immediate and named,
        // rather than an auth-service that will not start in an environment nobody tests.
        Map<String, Object> variables = environmentWithRsaKeys();
        variables.put("SECURITY_JWT_PRIVATE_KEY", "not-a-key");

        assertThatCode(() -> bindFromEnvironment(variables))
                .as("binding must ignore names that only look like accessors on this class")
                .doesNotThrowAnyException();
    }
}
