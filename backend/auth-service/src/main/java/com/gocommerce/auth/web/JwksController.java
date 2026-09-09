package com.gocommerce.auth.web;

import com.gocommerce.platform.security.JwksDocument;
import com.gocommerce.platform.security.JwtProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Public verification keys. The overlap key remains here until old tokens expire. */
@RestController
public class JwksController {

    private final JwtProperties properties;

    public JwksController(JwtProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/v1/auth/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(JwksDocument.from(properties));
    }
}
