package com.threatscope.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthResponse {
    private String overall;
    private Map<String, Object> components;
    private LocalDateTime timestamp;
}
