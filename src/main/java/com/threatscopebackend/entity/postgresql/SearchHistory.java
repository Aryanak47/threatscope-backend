package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_history")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"user"})
public class SearchHistory {
    
    public enum SearchType {
        EMAIL,
        DOMAIN,
        IP_ADDRESS,
        KEYWORD,
        USERNAME,
        PHONE_NUMBER,
        CREDIT_CARD,
        HASH,
        CUSTOM_QUERY
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "search_type", nullable = false)
    private SearchType searchType;
    
    @Column(name = "query", nullable = false, columnDefinition = "TEXT")
    private String query;
    
    @Column(name = "filters", columnDefinition = "TEXT")
    private String filters; // JSON string of filters
    
    @Column(name = "result_count")
    private Integer resultCount = 0;
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
    
    @Column(name = "source")
    private String source; // WEB, API, SCHEDULED, etc.
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "is_saved")
    private boolean isSaved = false;
    
    @Column(name = "saved_name")
    private String savedName;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_accessed")
    private LocalDateTime lastAccessed;
    
    @Column(name = "access_count")
    private Integer accessCount = 1;
    
    @Column(name = "is_bookmarked")
    private boolean isBookmarked = false;
    
    @Column(name = "share_token")
    private String shareToken;
    
    @Column(name = "is_shared")
    private boolean isShared = false;
    
    @Column(name = "search_metadata", columnDefinition = "TEXT")
    private String searchMetadata; // JSON string for additional search metadata
    
    // Increment access count and update last accessed timestamp
    public void recordAccess() {
        this.accessCount++;
        this.lastAccessed = LocalDateTime.now();
    }
}
