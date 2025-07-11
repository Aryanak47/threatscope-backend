package com.threatscopebackend.service.core;

import com.threatscopebackend.dto.request.UpdateUserRequest;
import com.threatscopebackend.dto.response.UserProfileResponse;
import com.threatscopebackend.entity.postgresql.Role;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.RoleRepository;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for user management operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Get user by ID
     */
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * Get user by email
     */
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    /**
     * Get current user profile
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UserPrincipal currentUser) {
        User user = findById(currentUser.getId());
        return mapToUserProfile(user);
    }

    /**
     * Update user profile
     */
    @Transactional
    public UserProfileResponse updateUserProfile(UserPrincipal currentUser, UpdateUserRequest request) {
        User user = findById(currentUser.getId());

        // Update basic info
        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName());
        }
        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName());
        }
        if (StringUtils.hasText(request.getPhoneNumber())) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (StringUtils.hasText(request.getCompany())) {
            user.setCompany(request.getCompany());
        }

        // Update password if provided
        if (StringUtils.hasText(request.getCurrentPassword()) && StringUtils.hasText(request.getNewPassword())) {
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        User updatedUser = userRepository.save(user);
        log.info("User {} updated their profile", updatedUser.getEmail());
        
        return mapToUserProfile(updatedUser);
    }

    /**
     * Get all users (for admin)
     */
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::mapToUserProfile);
    }

    /**
     * Update user roles (for admin)
     */
    @Transactional
    public UserProfileResponse updateUserRoles(Long userId, Set<String> roleNames) {
        User user = findById(userId);
        
        Set<Role> roles = roleNames.stream()
                .map(roleName -> {
                    try {
                        Role.RoleName roleNameEnum = Role.RoleName.valueOf(roleName);
                        return roleRepository.findByName(roleNameEnum)
                                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
                    } catch (IllegalArgumentException e) {
                        throw new ResourceNotFoundException("Role", "name", roleName);
                    }
                })
                .collect(Collectors.toSet());
        
        user.setRoles(roles);
        User updatedUser = userRepository.save(user);
        
        log.info("Updated roles for user {}", updatedUser.getEmail());
        return mapToUserProfile(updatedUser);
    }

    /**
     * Toggle user active status (for admin)
     */
    @Transactional
    public UserProfileResponse toggleUserStatus(Long userId, boolean active) {
        User user = findById(userId);
        user.setActive(active);
        
        User updatedUser = userRepository.save(user);
        log.info("User {} is now {}", updatedUser.getEmail(), active ? "active" : "inactive");
        
        return mapToUserProfile(updatedUser);
    }


    /**
     * Convert User entity to UserProfileResponse DTO
     */
    private UserProfileResponse mapToUserProfile(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name()) // Convert Role.RoleName to String
                .collect(Collectors.toSet());
        
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .company(user.getCompany())
                .roles(roles)
                .isActive(user.isActive())
                .isEmailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
