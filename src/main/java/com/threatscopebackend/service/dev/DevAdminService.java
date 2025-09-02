package com.threatscopebackend.service.dev;

import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.entity.postgresql.Role;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.repository.postgresql.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEVELOPMENT ONLY SERVICE
 * This service is only active in 'dev' profile for testing admin functionality
 * DO NOT USE IN PRODUCTION
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dev") // Only active in development profile
public class DevAdminService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    
    /**
     * DEVELOPMENT ONLY: Promote user to admin for testing
     * This method should NEVER be exposed via REST endpoint in production
     */
    @Transactional
    public void promoteUserToAdmin(String email) {
        log.warn("🚨 DEVELOPMENT ONLY: Promoting user {} to admin role", email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));
        
        // Check if user already has admin role
        boolean hasAdminRole = user.hasRole(Role.RoleName.ROLE_ADMIN);
        
        if (!hasAdminRole) {
            // Find or create admin role
            Role adminRole = roleRepository.findByName(Role.RoleName.ROLE_ADMIN)
                .orElseGet(() -> {
                    Role newRole = new Role(Role.RoleName.ROLE_ADMIN);
                    return roleRepository.save(newRole);
                });
            
            // Add admin role to user
            user.addRole(adminRole);
            userRepository.save(user);
            
            log.info("✅ User {} promoted to admin role", email);
        } else {
            log.info("ℹ️ User {} already has admin role", email);
        }
    }
    
    /**
     * DEVELOPMENT ONLY: Remove admin role from user
     */
    @Transactional
    public void removeAdminRole(String email) {
        log.warn("🚨 DEVELOPMENT ONLY: Removing admin role from user {}", email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));
        
        // Find admin role and remove it from user
        Role adminRole = roleRepository.findByName(Role.RoleName.ROLE_ADMIN)
            .orElseThrow(() -> new RuntimeException("Admin role not found"));
        
        user.removeRole(adminRole);
        userRepository.save(user);
        
        log.info("✅ Admin role removed from user {}", email);
    }
    
    /**
     * DEVELOPMENT ONLY: Add any role to user
     */
    @Transactional
    public void addRoleToUser(String email, Role.RoleName roleName) {
        log.warn("🚨 DEVELOPMENT ONLY: Adding role {} to user {}", roleName, email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));
        
        if (!user.hasRole(roleName)) {
            Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role newRole = new Role(roleName);
                    return roleRepository.save(newRole);
                });
            
            user.addRole(role);
            userRepository.save(user);
            
            log.info("✅ Role {} added to user {}", roleName, email);
        } else {
            log.info("ℹ️ User {} already has role {}", email, roleName);
        }
    }
    
    /**
     * DEVELOPMENT ONLY: Remove any role from user
     */
    @Transactional
    public void removeRoleFromUser(String email, Role.RoleName roleName) {
        log.warn("🚨 DEVELOPMENT ONLY: Removing role {} from user {}", roleName, email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));
        
        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        
        user.removeRole(role);
        userRepository.save(user);
        
        log.info("✅ Role {} removed from user {}", roleName, email);
    }
    
    /**
     * DEVELOPMENT ONLY: Check if user has specific role
     */
    public boolean userHasRole(String email, Role.RoleName roleName) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));
        
        return user.hasRole(roleName);
    }
    
    /**
     * DEVELOPMENT ONLY: Get all roles for user
     */
    public java.util.Set<Role> getUserRoles(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));
        
        return user.getRoles();
    }
}
