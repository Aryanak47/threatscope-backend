package com.threatscopebackend.dto.consultation.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteSessionRequest {
    
    @NotBlank(message = "Expert summary is required")
    @Size(max = 2000, message = "Summary must not exceed 2000 characters")
    private String summary;
    
    private List<String> deliverables;
    
    @Size(max = 1000, message = "Expert feedback must not exceed 1000 characters")
    private String expertFeedback;
}
