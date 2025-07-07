package com.threatscope.dto;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class TimelineEvent {
    
    private String id;
    
    private String email;
    
    private String domain;
    
    private String source;
    
    private LocalDateTime date;
    
    private String eventType;
    
    private String severity;
    
    private Map<String, Object> details;
}
