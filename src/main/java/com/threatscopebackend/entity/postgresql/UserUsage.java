package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_usage", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "usage_date"})
})
@Data
@NoArgsConstructor
public class UserUsage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;
    
    // Search tracking
    @Column(name = "searches_count", nullable = false)
    private int searchesCount = 0;
    
    @Column(name = "last_search_at")
    private LocalDateTime lastSearchAt;
    
    // Export tracking
    @Column(name = "exports_count", nullable = false)
    private int exportsCount = 0;
    
    @Column(name = "last_export_at")
    private LocalDateTime lastExportAt;
    
    // API usage tracking
    @Column(name = "api_calls_count", nullable = false)
    private int apiCallsCount = 0;
    
    @Column(name = "last_api_call_at")
    private LocalDateTime lastApiCallAt;
    
    // Monitoring tracking
    @Column(name = "monitoring_items_created", nullable = false)
    private int monitoringItemsCreated = 0;
    
    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Helper methods
    public void incrementSearches() {
        this.searchesCount++;
        this.lastSearchAt = LocalDateTime.now();
    }
    
    public void incrementExports() {
        this.exportsCount++;
        this.lastExportAt = LocalDateTime.now();
    }
    
    public void incrementApiCalls() {
        this.apiCallsCount++;
        this.lastApiCallAt = LocalDateTime.now();
    }
    
    public void incrementMonitoringItems() {
        this.monitoringItemsCreated++;
    }
    
    public boolean canSearch(int dailyLimit) {
        return this.searchesCount < dailyLimit;
    }
    
    public boolean canExport(int dailyLimit) {
        return this.exportsCount < dailyLimit;
    }
    
    public boolean canMakeApiCall(int dailyLimit) {
        return this.apiCallsCount < dailyLimit;
    }
}
