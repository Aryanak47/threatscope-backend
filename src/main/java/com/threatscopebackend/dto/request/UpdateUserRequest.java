package com.threatscopebackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



/**
 * DTO for updating user profile information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    @Size(max = 50, message = "First name must be less than 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must be less than 50 characters")
    private String lastName;

    @Email(message = "Email should be valid")
    private String email;

    @Size(max = 20, message = "Phone number must be less than 20 characters")
    private String phoneNumber;

    @Size(max = 100, message = "Company name must be less than 100 characters")
    private String company;

    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String currentPassword;

    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String newPassword;
}
