package com.threatscopebackend.dto.response;

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
}
