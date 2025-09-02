package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "anonymous_usage", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"ip_address", "usage_date"})
})
@Data
@NoArgsConstructor
public class AnonymousUsage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ip_address", nullable = false, length = 45) // Support IPv6
    private String ipAddress;
    
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;
    
    @Column(name = "searches_count", nullable = false)
    private int searchesCount = 0;
    
    @Column(name = "last_search_at")
    private LocalDateTime lastSearchAt;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "country_code", length = 2)
    private String countryCode;
    
    // Session tracking to prevent abuse
    @Column(name = "session_id")
    private String sessionId;
    
    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public void incrementSearches() {
        this.searchesCount++;
        this.lastSearchAt = LocalDateTime.now();
    }
    
    public boolean canSearch(int dailyLimit) {
        return this.searchesCount < dailyLimit;
    }
}
