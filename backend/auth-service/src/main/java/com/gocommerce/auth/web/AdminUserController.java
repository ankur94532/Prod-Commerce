package com.gocommerce.auth.web;

import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserController.class);

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    // ---------- LIST ----------

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> result = adminUserService.listUsers(pageable);

        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        Map<String, Object> body = Map.of(
                "items", items,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        );

        logger.info("Admin user list page={} size={} total={}", page, size, result.getTotalElements());
        return ResponseEntity.ok(body);
    }

    // ---------- GET ONE ----------

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable String id) {
        User user = adminUserService.getUser(id);
        return ResponseEntity.ok(Map.of("item", toSummary(user)));
    }

    // ---------- UPDATE ----------

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @Valid @RequestBody AdminUserUpdateRequest request
    ) {
        User updated = adminUserService.updateUser(
                id,
                request.getFullName(),
                request.getRole()
        );
        logger.info("Updated user id={} email={} role={}",
                updated.getId(), updated.getEmail(), updated.getRole().name());

        return ResponseEntity.ok(Map.of("item", toSummary(updated)));
    }

    // ---------- DELETE (hard delete) ----------

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id, Authentication authentication) {
        User target = adminUserService.getUser(id);

        String currentEmail = authentication != null ? authentication.getName() : null;
        if (currentEmail != null && currentEmail.equalsIgnoreCase(target.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot delete your own account."
            );
        }

        adminUserService.deleteUser(id);
        logger.info("Deleted user id={} email={}", target.getId(), target.getEmail());
        return ResponseEntity.noContent().build();
    }

    // ---------- Helpers ----------

    private Map<String, Object> toSummary(User u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("email", u.getEmail());
        map.put("fullName", u.getFullName());
        map.put("role", u.getRole().name());
        map.put("createdAt", u.getCreatedAt());
        map.put("updatedAt", u.getUpdatedAt());
        return map;
    }

    // ---------- DTO for update ----------

    public static class AdminUserUpdateRequest {

        @NotBlank
        private String fullName;

        @NotBlank
        private String role; // "USER" or "ADMIN"

        public AdminUserUpdateRequest() {
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
