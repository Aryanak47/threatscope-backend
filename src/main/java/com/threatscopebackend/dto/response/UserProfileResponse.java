package com.threatscopebackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO for user profile responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String company;
    private Set<String> roles;
    private boolean isActive;
    private boolean isEmailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
