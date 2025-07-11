package com.threatscopebackend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchRequest {
    
    @NotBlank(message = "Search query cannot be empty")
    private String query;

    @Builder.Default
    private SearchType searchType = SearchType.AUTO;
    @Builder.Default
    private SearchMode searchMode = SearchMode.PARTIAL;
    
    @Min(value = 0, message = "Page must be >= 0")
    @Builder.Default
    private int page = 0;
    
    @Min(value = 1, message = "Size must be >= 1")
    @Max(value = 100, message = "Size must be <= 100")
    @Builder.Default
    private int size = 20;

    @Builder.Default
    @Pattern(regexp = "^(timestamp)$",
             message = "Invalid sort field. Must be one of: timestamp, login, url")
    private String sortBy = "timestamp";

    @Builder.Default
    @Pattern(regexp = "^(?i)(asc|desc)$", message = "Sort direction must be 'asc' or 'desc'")
    private String sortDirection = "desc";
    
    private Map<String, Object> filters;
    
    private boolean includeMetadata = false;
    
    private boolean highlightResults = false;
    
    public enum SearchType {
        AUTO,           // Auto-detect based on query
        EMAIL,          // Email address search
        DOMAIN,         // Domain search
        USERNAME,       // Username search
        PASSWORD,       // Password hash search
//        PHONE,          // Phone number search
//        IP_ADDRESS,     // IP address search
        URL,            // URL search
        ADVANCED        // Advanced multi-field search
    }
    
    public enum SearchMode {
        EXACT,          // Exact match
        PARTIAL,        // Partial/fuzzy match
        DOMAIN_ONLY,    // Extract and search domain only (for emails)
        USERNAME_ONLY,  // Extract and search username only (for emails)
        WILDCARD        // Wildcard search
    }
}
