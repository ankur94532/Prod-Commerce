package com.gocommerce.auth.service;

import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import com.gocommerce.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository);
    }

    private User buildUser(String email, Role role) {
        return new User(email, "hash", "Name", role) {
            @Override
            public String getId() {
                return "id-" + email;
            }
        };
    }

    @Test
    void listUsers_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        List<User> users = List.of(buildUser("u1@example.com", Role.USER));
        Page<User> page = new PageImpl<>(users, pageable, users.size());

        when(userRepository.findAll(pageable)).thenReturn(page);

        Page<User> result = service.listUsers(pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).findAll(pageable);
    }

    @Test
    void getUser_returnsUserWhenExists() {
        User user = buildUser("u1@example.com", Role.USER);
        when(userRepository.findById("id-u1@example.com")).thenReturn(Optional.of(user));

        User result = service.getUser("id-u1@example.com");

        assertThat(result).isSameAs(user);
    }

    @Test
    void getUser_throwsWhenNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getUser("missing"));
    }

    @Test
    void updateUser_updatesFullNameAndRole() {
        User user = buildUser("u1@example.com", Role.USER);
        when(userRepository.findById("id-u1@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = service.updateUser("id-u1@example.com", "New Name", "ADMIN");

        assertThat(updated.getFullName()).isEqualTo("New Name");
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("New Name");
    }

    @Test
    void deleteUser_deletesExistingUser() {
        User user = buildUser("u1@example.com", Role.USER);
        when(userRepository.findById("id-u1@example.com")).thenReturn(Optional.of(user));

        service.deleteUser("id-u1@example.com");

        verify(userRepository).delete(user);
    }
}
