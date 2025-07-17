package com.threatscopebackend.controller.user;

import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.core.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserTestController {

    private final UserService userService;

    /**
     * Test endpoint to check authentication and user data
     */
    @GetMapping("/test")
    public ResponseEntity<ApiResponse<Object>> testUserEndpoint(@CurrentUser UserPrincipal userPrincipal) {
        try {
            if (userPrincipal == null) {
                log.warn("Test endpoint called with null user principal");
                return ResponseEntity.ok(ApiResponse.success("Test successful", 
                    java.util.Map.of(
                        "authenticated", false,
                        "message", "No user principal found - security might be disabled"
                    )));
            }
            
            log.info("Test endpoint called by user: {}", userPrincipal.getId());
            
            // Try to fetch user details
            User user = userService.findById(userPrincipal.getId());
            
            return ResponseEntity.ok(ApiResponse.success("Test successful", 
                java.util.Map.of(
                    "authenticated", true,
                    "userId", user.getId(),
                    "email", user.getEmail(),
                    "firstName", user.getFirstName(),
                    "lastName", user.getLastName(),
                    "hasSubscription", user.getSubscription() != null,
                    "subscriptionType", user.getSubscription() != null ? 
                        user.getSubscription().getPlanType().name() : "NONE",
                    "roles", user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .toList()
                )));
                
        } catch (Exception e) {
            log.error("Error in test endpoint", e);
            return ResponseEntity.badRequest().body(
                ApiResponse.error("Test failed: " + e.getMessage()));
        }
    }
}
