package com.gocommerce.auth.web;

import com.gocommerce.auth.dto.UserResponse;
import com.gocommerce.auth.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse user = new UserResponse(
                principal.getId(),
                principal.getUsername(),
                principal.getFullName(),
                principal.getRole(),
                principal.getCreatedAt());
        return ResponseEntity.ok(Map.of("data", user));
    }
}
