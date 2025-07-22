package com.threatscopebackend.service.core;



import com.threatscopebackend.dto.request.LoginRequest;
import com.threatscopebackend.dto.request.PasswordResetRequest;
import com.threatscopebackend.dto.request.RegisterRequest;
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
import com.threatscopebackend.service.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SubscriptionService subscriptionService;

    @Transactional
    public AuthResponse authenticateUser(LoginRequest loginRequest, String ipAddress) {
        try {
            log.info("🔍 LOGIN ATTEMPT: Starting authentication for email: {}", loginRequest.getEmail());
            
            // Check if user exists first (with subscription data)
            User user = userRepository.findByEmailWithRolesAndSubscription(loginRequest.getEmail())
                    .orElseThrow(() -> {
                        log.warn("❌ User not found with email: {}", loginRequest.getEmail());
                        return new BadRequestException("Invalid email or password");
                    });
            
            log.info("🔍 Found user: {} (ID: {}, Active: {}, EmailVerified: {}, Plan: {})", 
                    user.getEmail(), user.getId(), user.isActive(), user.isEmailVerified(),
                    user.getSubscription() != null ? user.getSubscription().getPlanType() : "No subscription");
            
            // Debug: Show what UserPrincipal.enabled will be
            boolean willBeEnabled = user.isActive() && user.isEmailVerified();
            log.info("🔍 UserPrincipal will be enabled: {} (Active: {} && EmailVerified: {})", 
                    willBeEnabled, user.isActive(), user.isEmailVerified());
            
            // Check if user is active
            if (!user.isActive()) {
                log.warn("❌ User account is inactive: {}", loginRequest.getEmail());
                throw new BadRequestException("Account is inactive. Please contact support.");
            }
            
            log.info("🔍 Attempting Spring Security authentication...");
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );
            log.info("✅ Spring Security authentication successful");

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            
            // Update last login (with subscription data loaded)
            User foundUser = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
            
            // Ensure user has a subscription (for existing users who may not have one)
            if (foundUser.getSubscription() == null) {
                log.info("🔍 User {} has no subscription, creating free subscription", foundUser.getId());
                try {
                    subscriptionService.createFreeSubscription(foundUser);
                    // Reload user with subscription
                    foundUser = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
                    log.info("🔍 Free subscription created for existing user: {}", foundUser.getId());
                } catch (Exception subEx) {
                    log.warn("⚠️ Failed to create free subscription for existing user {}: {}", foundUser.getId(), subEx.getMessage());
                }
            }
            
            foundUser.setLastLogin(java.time.LocalDateTime.now());
            userRepository.save(foundUser);
            
            log.info("🔍 Generating tokens...");
            // Generate tokens
            String accessToken = tokenProvider.generateToken(authentication);
            String refreshToken = tokenProvider.generateRefreshToken(authentication);
            log.info("✅ Tokens generated successfully");
            
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

            log.info("✅ LOGIN SUCCESS: User {} authenticated successfully", loginRequest.getEmail());
            // Use the user with subscription data for the AuthResponse
            return new AuthResponse(accessToken, refreshToken, foundUser);
            
        } catch (BadRequestException e) {
            // Re-throw our custom exceptions
            log.error("❌ LOGIN FAILED: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // Log failed login attempt with more details
            log.error("❌ LOGIN FAILED: Authentication failed for email: {} with error: {}", 
                    loginRequest.getEmail(), e.getMessage(), e);
            log.error("🔍 Exception type: {}", e.getClass().getSimpleName());
            
            // Check if it's a specific Spring Security exception
            if (e.getMessage().contains("Bad credentials")) {
                throw new BadRequestException("Invalid email or password");
            } else if (e.getMessage().contains("User is disabled")) {
                throw new BadRequestException("Account is disabled. Please contact support.");
            } else if (e.getMessage().contains("User account is locked")) {
                throw new BadRequestException("Account is locked. Please contact support.");
            } else {
                throw new BadRequestException("Invalid email or password");
            }
        }
    }

    @Transactional
    public AuthResponse registerUser(RegisterRequest registerRequest, String ipAddress) {
        try {
            log.info("🔍 Step 1: Checking if email exists: {}", registerRequest.getEmail());
            if (userRepository.existsByEmail(registerRequest.getEmail())) {
                log.warn("⚠️ Email already exists: {}", registerRequest.getEmail());
                throw new BadRequestException("Email address already in use");
            }

            log.info("🔍 Step 2: Creating new user object");
            // Create new user
            User user = new User();
            user.setFirstName(registerRequest.getFirstName());
            user.setLastName(registerRequest.getLastName());
            user.setEmail(registerRequest.getEmail());
            
            log.info("🔍 Step 3: Encoding password");
            user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
            user.setPhoneNumber(registerRequest.getPhoneNumber());
            
            // Set optional fields (company and jobTitle)
            if (registerRequest.getCompany() != null && !registerRequest.getCompany().trim().isEmpty()) {
                user.setCompany(registerRequest.getCompany().trim());
                log.info("🔍 Setting company: {}", registerRequest.getCompany());
            }
            if (registerRequest.getJobTitle() != null && !registerRequest.getJobTitle().trim().isEmpty()) {
                user.setJobTitle(registerRequest.getJobTitle().trim());
                log.info("🔍 Setting job title: {}", registerRequest.getJobTitle());
            }
            
            user.setActive(true);
            user.setEmailVerified(false);
            user.setEmailVerificationToken(UUID.randomUUID().toString());
            
            log.info("🔍 User status: Active={}, EmailVerified={}", user.isActive(), user.isEmailVerified());
            
            log.info("🔍 Step 4: Finding ROLE_USER");
            // Assign default role
            Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "ROLE_USER"));
            log.info("🔍 Found role: {}", userRole.getName());
            
            // Use helper method to safely add role
            user.getRoles().add(userRole);
            
            log.info("🔍 Step 5: Saving user to database");
            // Save user
            User result = userRepository.save(user);
            log.info("🔍 User saved with ID: {}", result.getId());
            
            log.info("🔍 Step 5.5: Creating free subscription for new user");
            // Create free subscription for new user
            try {
                subscriptionService.createFreeSubscription(result);
                log.info("🔍 Free subscription created successfully for user: {}", result.getId());
            } catch (Exception subEx) {
                log.warn("⚠️ Failed to create free subscription for user {}: {}", result.getId(), subEx.getMessage());
                // Don't fail registration if subscription creation fails
            }
            
            log.info("🔍 Step 6: Sending verification email");
            // Send verification email
            try {
                emailService.sendVerificationEmail(user);
                log.info("🔍 Verification email sent successfully");
            } catch (Exception emailEx) {
                log.warn("⚠️ Failed to send verification email: {}", emailEx.getMessage());
                // Don't fail registration if email fails
            }
            
            log.info("🔍 Step 7: Generating tokens");
            // Generate tokens
            String accessToken = tokenProvider.generateTokenFromUserId(result.getId());
            log.info("🔍 Access token generated");
            
            String refreshToken = tokenProvider.generateRefreshTokenFromUserId(result.getId());
            log.info("🔍 Refresh token generated");
            
            log.info("🔍 Step 8: Creating UserPrincipal");
            UserPrincipal userPrincipal = UserPrincipal.create(result);
            log.info("🔍 UserPrincipal created");
            
            log.info("✅ Registration completed successfully for: {}", registerRequest.getEmail());
            return new AuthResponse(accessToken, refreshToken, userPrincipal);
        } catch (Exception e) {
            log.error("❌ Registration failed at some step for email: {} - Error: {}", 
                    registerRequest.getEmail(), e.getMessage(), e);
            throw e;
        }
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
        User user = userRepository.findByIdWithRolesAndSubscription(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Generate new tokens
        String newAccessToken = tokenProvider.generateTokenFromUserId(userId);
        String newRefreshToken = tokenProvider.generateRefreshTokenFromUserId(userId);
        
        return new AuthResponse(newAccessToken, newRefreshToken, user);
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
