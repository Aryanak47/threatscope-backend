package com.threatscopebackend.service.core;


import com.threatscope.dto.request.PasswordResetRequest;
import com.threatscope.dto.request.RegisterRequest;

import com.threatscopebackend.dto.request.LoginRequest;
import com.threatscopebackend.dto.response.AuthResponse;
import com.threatscopebackend.entity.postgresql.Role;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.BadRequestException;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.RoleRepository;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.security.JwtTokenProvider;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.notification.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;

    @Transactional
    public AuthResponse authenticateUser(LoginRequest loginRequest, String ipAddress) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            
            // Update last login
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
            user.setLastLogin(java.time.LocalDateTime.now());
            userRepository.save(user);

            // Generate tokens
            String accessToken = tokenProvider.generateToken(authentication);
            String refreshToken = tokenProvider.generateRefreshToken(authentication);
            
            // Log successful login
//            auditService.logUserAction(
//                    userPrincipal.getId(),
//                    AuditLog.AuditAction.USER_LOGIN,
//                    "User",
//                    userPrincipal.getId().toString(),
//                    null,
//                    null,
//                    ipAddress,
//                    null,
//                    null
//            );

            return new AuthResponse(accessToken, refreshToken, userPrincipal);
            
        } catch (Exception e) {
            // Log failed login attempt
//            auditService.logFailedLogin(
//                    loginRequest.getEmail(),
//                    ipAddress,
//                    null,
//                    e.getMessage()
//            );
            throw new BadRequestException("Invalid email or password");
        }
    }

    @Transactional
    public AuthResponse registerUser(RegisterRequest registerRequest, String ipAddress) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new BadRequestException("Email address already in use");
        }

        // Create new user
        User user = new User();
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setPhoneNumber(registerRequest.getPhoneNumber());
        user.setActive(true);
        user.setEmailVerified(false);
        user.setEmailVerificationToken(UUID.randomUUID().toString());
        
        // Assign default role
        Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "ROLE_USER"));
        user.setRoles(Collections.singleton(userRole));
        
        // Save user
        User result = userRepository.save(user);
        
        // Send verification email
        emailService.sendVerificationEmail(user);
        
        // Log user registration
//        auditService.logUserAction(
//                user.getId(),
//                AuditLog.AuditAction.USER_CREATED,
//                "User",
//                user.getId().toString(),
//                null,
//                null,
//                ipAddress,
//                null,
//                null
//        );
        
        // Generate tokens
        String accessToken = tokenProvider.generateTokenFromUserId(user.getId());
        String refreshToken = tokenProvider.generateRefreshToken(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        user.getPassword()
                )
        );
        
        return new AuthResponse(accessToken, refreshToken, UserPrincipal.create(user));
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));
        
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email already verified");
        }
        
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
        
        // Log email verification
//        auditService.logUserAction(
//                user.getId(),
//                AuditLog.AuditAction.USER_UPDATED,
//                "User",
//                user.getId().toString(),
//                Map.of("emailVerified", false),
//                Map.of("emailVerified", true),
//                null,
//                null,
//                null
//        );
    }

    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpires(java.time.LocalDateTime.now().plusHours(24));
        userRepository.save(user);
        
        // Send password reset email
        emailService.sendPasswordResetEmail(user, token);
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid password reset token"));
        
        if (user.getPasswordResetExpires().isBefore(java.time.LocalDateTime.now())) {
            throw new BadRequestException("Password reset token has expired");
        }
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        userRepository.save(user);
//
//        // Log password reset
//        auditService.logUserAction(
//                user.getId(),
//                AuditLog.AuditAction.USER_UPDATED,
//                "User",
//                user.getId().toString(),
//                null,
//                Map.of("password", "[PROTECTED]"),
//                null,
//                null,
//                null
//        );
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid refresh token");
        }
        
        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Generate new tokens
        String newAccessToken = tokenProvider.generateTokenFromUserId(userId);
        String newRefreshToken = tokenProvider.generateRefreshToken(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        user.getPassword()
                )
        );
        
        return new AuthResponse(newAccessToken, newRefreshToken, UserPrincipal.create(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        if (tokenProvider.validateToken(refreshToken)) {
            Long userId = tokenProvider.getUserIdFromToken(refreshToken);
            
//            // Log logout action
//            auditService.logUserAction(
//                    userId,
//                    AuditLog.AuditAction.USER_LOGOUT,
//                    "User",
//                    userId.toString(),
//                    null,
//                    null,
//                    null,
//                    null,
//                    null
//            );
        }
    }
}
