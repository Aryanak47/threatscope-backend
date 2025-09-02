package com.threatscopebackend.dto.response;

import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.security.UserPrincipal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
    
    // Include full user object to match frontend expectations
    private User user;
    
    // Legacy fields for backward compatibility
    private Long id;
    private String name;
    private String email;
    private List<String> roles;
    private boolean isEmailVerified;
    private boolean isMfaEnabled;
    private String mfaType;
    private String profileImageUrl;
    private boolean isOnboarded;

    public AuthResponse(String accessToken, String refreshToken, UserPrincipal userPrincipal) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.expiresIn = 86400L; // 24 hours in seconds
        
        // Set full user object
        this.user = userPrincipal.getUser();
        
        // Legacy fields for backward compatibility
        this.id = userPrincipal.getId();
        this.name = userPrincipal.getName();
        this.email = userPrincipal.getEmail();
        this.roles = userPrincipal.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());
        this.isEmailVerified = userPrincipal.isEnabled();
        this.isMfaEnabled = false; // Set based on your MFA implementation
        this.mfaType = null; // Set based on your MFA implementation
        this.profileImageUrl = null; // Set if you have user profile images
        this.isOnboarded = false; // Set based on your onboarding flow
    }
    
    // Constructor that takes User directly
    public AuthResponse(String accessToken, String refreshToken, User user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.expiresIn = 86400L; // 24 hours in seconds
        
        // Set full user object
        this.user = user;
        
        // Legacy fields for backward compatibility
        this.id = user.getId();
        this.name = user.getFirstName() + " " + user.getLastName();
        this.email = user.getEmail();
        this.roles = user.getRoles().stream()
                .map(role -> role.getName().toString())
                .collect(Collectors.toList());
        this.isEmailVerified = user.isEmailVerified();
        this.isMfaEnabled = user.isTwoFactorEnabled();
        this.mfaType = null;
        this.profileImageUrl = user.getAvatarUrl();
        this.isOnboarded = true; // Assume onboarded if user exists
    }
}
