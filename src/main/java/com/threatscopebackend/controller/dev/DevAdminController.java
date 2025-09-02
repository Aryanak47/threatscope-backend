package com.threatscopebackend.controller.dev;

import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.entity.postgresql.Role;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.repository.postgresql.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * DEVELOPMENT ONLY CONTROLLER
 * This controller is only active in 'dev' profile for testing admin functionality
 * DO NOT USE IN PRODUCTION - REMOVE BEFORE DEPLOYMENT
 */
@RestController
@RequestMapping("/dev/admin")
@RequiredArgsConstructor
@Slf4j
@Profile("dev") // Only active in development profile
public class DevAdminController {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    
    /**
     * DEVELOPMENT ONLY: Assign admin role to user for testing
     * POST /api/dev/admin/assign-role
     * Body: {"email": "user@example.com", "role": "ADMIN"}
     */
    @PostMapping("/assign-role")
    public ResponseEntity<ApiResponse<String>> assignAdminRole(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String roleStr = request.get("role");
        
        log.warn("🚨 DEV ONLY: Assigning {} role to user {}", roleStr, email);
        
        try {
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
            
            // Convert string to RoleName enum
            Role.RoleName roleName;
            try {
                String roleEnumName = roleStr.toUpperCase().startsWith("ROLE_") 
                    ? roleStr.toUpperCase() 
                    : "ROLE_" + roleStr.toUpperCase();
                roleName = Role.RoleName.valueOf(roleEnumName);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid role: " + roleStr + ". Valid roles: USER, ADMIN, MODERATOR, ANALYST, ENTERPRISE");
            }
            
            // Check if user already has this role
            boolean hasRole = user.hasRole(roleName);
            
            if (!hasRole) {
                // Find or create the role
                Role role = roleRepository.findByName(roleName)
                    .orElseGet(() -> {
                        Role newRole = new Role(roleName);
                        return roleRepository.save(newRole);
                    });
                
                // Add role to user
                user.addRole(role);
                userRepository.save(user);
                
                log.info("✅ DEV: Role {} assigned to user {}", roleName, email);
                return ResponseEntity.ok(ApiResponse.success(
                    "Role assigned successfully", 
                    String.format("User %s now has role %s", email, roleName)
                ));
            } else {
                log.info("ℹ️ DEV: User {} already has role {}", email, roleName);
                return ResponseEntity.ok(ApiResponse.success(
                    "Role already exists", 
                    String.format("User %s already has role %s", email, roleName)
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ DEV: Error assigning role: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.<String>error("Failed to assign role: " + e.getMessage()));
        }
    }
    
    /**
     * DEVELOPMENT ONLY: Remove role from user
     * DELETE /api/dev/admin/remove-role
     * Body: {"email": "user@example.com", "role": "ADMIN"}
     */
    @DeleteMapping("/remove-role")
    public ResponseEntity<ApiResponse<String>> removeRole(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String roleStr = request.get("role");
        
        log.warn("🚨 DEV ONLY: Removing {} role from user {}", roleStr, email);
        
        try {
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
            
            // Convert string to RoleName enum
            Role.RoleName roleName;
            try {
                String roleEnumName = roleStr.toUpperCase().startsWith("ROLE_") 
                    ? roleStr.toUpperCase() 
                    : "ROLE_" + roleStr.toUpperCase();
                roleName = Role.RoleName.valueOf(roleEnumName);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid role: " + roleStr + ". Valid roles: USER, ADMIN, MODERATOR, ANALYST, ENTERPRISE");
            }
            
            // Find the role and remove it from user
            Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
            
            user.removeRole(role);
            userRepository.save(user);
            
            log.info("✅ DEV: Role {} removed from user {}", roleName, email);
            return ResponseEntity.ok(ApiResponse.success(
                "Role removed successfully", 
                String.format("Role %s removed from user %s", roleName, email)
            ));
            
        } catch (Exception e) {
            log.error("❌ DEV: Error removing role: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.<String>error("Failed to remove role: " + e.getMessage()));
        }
    }
    
    /**
     * DEVELOPMENT ONLY: Get user roles
     * GET /api/dev/admin/user-roles?email=user@example.com
     */
    @GetMapping("/user-roles")
    public ResponseEntity<ApiResponse<Object>> getUserRoles(@RequestParam String email) {
        log.info("🔍 DEV: Getting roles for user {}", email);
        
        try {
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
            
            var roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());
            
            var response = Map.of(
                "email", email,
                "userId", user.getId(),
                "roles", roleNames,
                "hasAdminRole", user.hasRole(Role.RoleName.ROLE_ADMIN),
                "hasUserRole", user.hasRole(Role.RoleName.ROLE_USER),
                "isActive", user.isActive()
            );
            
            return ResponseEntity.ok(ApiResponse.success("User roles retrieved", response));
            
        } catch (Exception e) {
            log.error("❌ DEV: Error getting user roles: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.<Object>error("Failed to get user roles: " + e.getMessage()));
        }
    }
    
    /**
     * DEVELOPMENT ONLY: List all available roles
     * GET /api/dev/admin/available-roles
     */
    @GetMapping("/available-roles")
    public ResponseEntity<ApiResponse<Object>> getAvailableRoles() {
        log.info("🔍 DEV: Getting all available roles");
        
        try {
            var availableRoles = Map.of(
                "roles", Role.RoleName.values(),
                "descriptions", Map.of(
                    "ROLE_USER", "Standard user with basic access",
                    "ROLE_ADMIN", "Administrator with full system access",
                    "ROLE_MODERATOR", "Moderator with content management access",
                    "ROLE_ANALYST", "Security analyst with advanced monitoring access",
                    "ROLE_ENTERPRISE", "Enterprise user with premium features"
                )
            );
            
            return ResponseEntity.ok(ApiResponse.success("Available roles retrieved", availableRoles));
            
        } catch (Exception e) {
            log.error("❌ DEV: Error getting available roles: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.<Object>error("Failed to get available roles: " + e.getMessage()));
        }
    }
}
