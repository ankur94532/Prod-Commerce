package com.gocommerce.auth.web;

import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import com.gocommerce.auth.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerTest {

    @Test
    void me_returnsUserResponseWrappedInData() {
        User user = new User("user@example.com", "hash", "User One", Role.USER) {
            @Override
            public String getId() {
                return "user-123";
            }
        };
        UserPrincipal principal = new UserPrincipal(user);
        UserController controller = new UserController();

        ResponseEntity<?> response = controller.me(principal);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        Object dataObj = body.get("data");
        assertThat(dataObj).isInstanceOf(com.gocommerce.auth.dto.UserResponse.class);

        var dto = (com.gocommerce.auth.dto.UserResponse) dataObj;
        assertThat(dto.getId()).isEqualTo("user-123");
        assertThat(dto.getEmail()).isEqualTo("user@example.com");
        assertThat(dto.getRole()).isEqualTo("USER");
    }
}
