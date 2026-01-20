package com.gocommerce.auth.service;

import com.gocommerce.auth.entity.User;
import com.gocommerce.auth.model.Role;
import com.gocommerce.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<User> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    public User updateUser(String id, String fullName, String roleStr) {
        User user = getUser(id);

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }

        if (roleStr != null && !roleStr.isBlank()) {
            try {
                Role role = Role.valueOf(roleStr.toUpperCase());
                user.setRole(role);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid role: " + roleStr);
            }
        }

        return userRepository.save(user);
    }

    public void deleteUser(String id) {
        User user = getUser(id);
        userRepository.delete(user);
    }
}
