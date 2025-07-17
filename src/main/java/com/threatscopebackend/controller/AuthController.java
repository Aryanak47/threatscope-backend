package com.threatscopebackend.controller;


import com.threatscope.dto.request.PasswordResetRequest;
import com.threatscope.dto.request.RegisterRequest;



import com.threatscopebackend.dto.request.LoginRequest;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.dto.response.AuthResponse;
import com.threatscopebackend.service.core.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
            @Valid @RequestBody RegisterRequest registerRequest,
            HttpServletRequest request) {
        
        String ipAddress = getClientIpAddress(request);
        AuthResponse authResponse = authService.registerUser(registerRequest, ipAddress);
        
        return ResponseEntity.ok(
                ApiResponse.success("User registered successfully", authResponse)
        );
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
