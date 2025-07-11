package com.threatscopebackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO representing breach data from external APIs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalBreachData {
    private String id;
    private String email;
    private String username;
    private String domain;
    private String source;
    private LocalDateTime breachDate;
    private LocalDateTime addedDate;
    private LocalDateTime modifiedDate;
    private String title;
    private String description;
    private List<String> dataClasses;
    private boolean isVerified;
    private boolean isFabricated;
    private boolean isSensitive;
    private boolean isRetired;
    private boolean isSpamList;
    private boolean isMalware;
    private String logoPath;
    private Map<String, Object> additionalInfo;
}
