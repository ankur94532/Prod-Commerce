package com.gocommerce.auth.security;

import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import com.gocommerce.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    private User buildUser() {
        return new User("user@example.com", "hash", "User One", Role.USER) {
            @Override
            public String getId() {
                return "user-1";
            }
        };
    }

    @Test
    void loadUserById_returnsUserPrincipal() {
        User user = buildUser();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        var details = service.loadUserById("user-1");

        assertThat(details).isInstanceOf(UserPrincipal.class);
        assertThat(details.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    void loadUserByUsername_returnsUserPrincipal() {
        User user = buildUser();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        var details = service.loadUserByUsername("user@example.com");

        assertThat(details).isInstanceOf(UserPrincipal.class);
        assertThat(details.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    void loadUserByUsername_throwsWhenNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing@example.com")
        );
    }
}
