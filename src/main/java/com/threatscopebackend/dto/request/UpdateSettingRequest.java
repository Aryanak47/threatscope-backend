package com.threatscopebackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSettingRequest {
    
    @NotBlank(message = "Value is required")
    private String value;
    
    private String description;
}
