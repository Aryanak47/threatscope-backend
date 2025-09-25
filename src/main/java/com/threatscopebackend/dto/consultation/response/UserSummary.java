package com.threatscopebackend.dto.consultation.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.threatscopebackend.entity.postgresql.User;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummary {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String planType;
    private LocalDateTime createdAt;
    
    public static UserSummary fromEntity(User user) {
        if (user == null) {
            return null;
        }
        
        String fullName = "";
        if (user.getFirstName() != null && user.getLastName() != null) {
            fullName = user.getFirstName() + " " + user.getLastName();
        } else if (user.getFirstName() != null) {
            fullName = user.getFirstName();
        } else if (user.getLastName() != null) {
            fullName = user.getLastName();
        } else {
            // If no name available, use email prefix
            fullName = user.getEmail().split("@")[0];
        }
        
        // Get plan type from subscription
        String planType = "FREE"; // Default
        if (user.getSubscription() != null && user.getSubscription().getPlanType() != null) {
            planType = user.getSubscription().getPlanType().toString();
        }
        
        return UserSummary.builder()
            .id(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .fullName(fullName)
            .planType(planType)
            .createdAt(user.getCreatedAt())
            .build();
    }
}
