package com.threatscopebackend.controller;


import com.threatscopebackend.dto.request.LoginRequest;
import com.threatscopebackend.dto.request.PasswordResetRequest;
import com.threatscopebackend.dto.request.RegisterRequest;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.dto.response.AuthResponse;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.core.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<String>> testEndpoint() {
        return ResponseEntity.ok(
                ApiResponse.success("Auth controller is working!", "Test successful")
        );
    }

    @PostMapping("/login")

    public ResponseEntity<ApiResponse<AuthResponse>> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        
        String ipAddress = getClientIpAddress(request);
        AuthResponse authResponse = authService.authenticateUser(loginRequest, ipAddress);
        
        return ResponseEntity.ok(
                ApiResponse.success("Authentication successful", authResponse)
        );
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> registerUser(
            @RequestBody RegisterRequest registerRequest,
            HttpServletRequest request) {
        
        try {
            log.info("🚀 Registration attempt for email: {}", registerRequest.getEmail());
            log.info("📄 Full request data: firstName={}, lastName={}, email={}, phoneNumber={}", 
                    registerRequest.getFirstName(), registerRequest.getLastName(), 
                    registerRequest.getEmail(), registerRequest.getPhoneNumber());
            
            String ipAddress = getClientIpAddress(request);
            log.info("🌍 IP Address: {}", ipAddress);
            
            log.info("🔍 Calling authService.registerUser...");
            AuthResponse authResponse = authService.registerUser(registerRequest, ipAddress);
            
            log.info("✅ Registration successful for email: {}", registerRequest.getEmail());
            return ResponseEntity.ok(
                    ApiResponse.success("User registered successfully", authResponse)
            );
        } catch (Exception e) {
            log.error("❌ Registration failed for email: {} with error: {}", 
                    registerRequest.getEmail(), e.getMessage(), e);
            log.error("🔍 Exception type: {}", e.getClass().getSimpleName());
            log.error("🔍 Exception message: '{}'", e.getMessage());
            log.error("🔍 Exception cause: {}", e.getCause());
            if (e.getCause() != null) {
                log.error("🔍 Cause message: '{}'", e.getCause().getMessage());
            }
            throw e; // Re-throw to let global exception handler deal with it
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(
            @RequestParam String token) {

        authService.verifyEmail(token);
        return ResponseEntity.ok(
                ApiResponse.success("Email verified successfully")
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>>forgotPassword(
            @RequestParam String email) {
        
        authService.requestPasswordReset(email);
        
        return ResponseEntity.ok(
                ApiResponse.success("Password reset email sent")
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody PasswordResetRequest passwordResetRequest) {
        
        authService.resetPassword(passwordResetRequest);
        
        return ResponseEntity.ok(
                ApiResponse.success("Password reset successful")
        );
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestParam String refreshToken) {
        
        AuthResponse authResponse = authService.refreshToken(refreshToken);
        
        return ResponseEntity.ok(
                ApiResponse.success("Token refreshed successfully", authResponse)
        );
    }
    
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        log.info("🔍 /auth/me endpoint called for user ID: {}", userPrincipal.getId());
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        log.info("✅ User data retrieved for: {} ({}) with plan: {}", 
                user.getEmail(), user.getId(), 
                user.getSubscription() != null ? user.getSubscription().getPlanType() : "No subscription");
        
        return ResponseEntity.ok(
                ApiResponse.success("User retrieved successfully", user)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @RequestParam String refreshToken) {
        
        authService.logout(refreshToken);
        
        return ResponseEntity.ok(
                ApiResponse.success("Logout successful")
        );
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
