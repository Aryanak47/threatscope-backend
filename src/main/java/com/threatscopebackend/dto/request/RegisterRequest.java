package com.threatscope.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    
    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{8,100}$",
        message = "Password must contain at least one digit, one lowercase, one uppercase and one special character"
    )
    private String password;
    
    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^\\+?[1-9]\\d{1,14}$",
        message = "Phone number must be in E.164 format"
    )
    private String phoneNumber;
    
    private boolean acceptTerms = false;
    
    private boolean subscribeToNewsletter = false;
}
