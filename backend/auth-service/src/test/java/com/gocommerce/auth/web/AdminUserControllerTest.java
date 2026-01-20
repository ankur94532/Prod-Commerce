package com.gocommerce.auth.web;

import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import com.gocommerce.auth.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminUserControllerTest {

    @Test
    void delete_throwsWhenAdminTriesToDeleteSelf() {
        User admin = new User("admin@example.com", "hash", "Admin", Role.ADMIN) {
            @Override
            public String getId() {
                return "admin-id";
            }
        };

        // Stub service: only methods used in delete()
        AdminUserService service = new AdminUserService(null) {
            @Override
            public User getUser(String id) {
                return admin;
            }

            @Override
            public void deleteUser(String id) {
                throw new AssertionError("deleteUser should not be called for self-delete");
            }
        };

        AdminUserController controller = new AdminUserController(service);
        Authentication auth = new TestingAuthenticationToken("admin@example.com", null);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.delete("admin-id", auth)
        );

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getReason()).contains("cannot delete your own");
    }

    @Test
    void delete_callsServiceWhenDeletingOtherUser() {
        User target = new User("target@example.com", "hash", "Target", Role.USER) {
            @Override
            public String getId() {
                return "target-id";
            }
        };

        class RecordingAdminUserService extends AdminUserService {
            String deletedId;

            RecordingAdminUserService() {
                super(null);
            }

            @Override
            public User getUser(String id) {
                return target;
            }

            @Override
            public void deleteUser(String id) {
                this.deletedId = id;
            }
        }

        RecordingAdminUserService service = new RecordingAdminUserService();
        AdminUserController controller = new AdminUserController(service);
        Authentication auth = new TestingAuthenticationToken("admin@example.com", null);

        var response = controller.delete("target-id", auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(service.deletedId).isEqualTo("target-id");
    }
}
