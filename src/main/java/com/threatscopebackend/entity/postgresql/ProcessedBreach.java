package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_breaches", 
       indexes = {
           @Index(name = "idx_monitoring_item_breach", columnList = "monitoring_item_id,breach_id"),
           @Index(name = "idx_processed_at", columnList = "processed_at")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_monitoring_breach", columnNames = {"monitoring_item_id", "breach_id"})
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedBreach {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitoring_item_id", nullable = false)
    private MonitoringItem monitoringItem;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "breach_alert_id")
    private BreachAlert breachAlert; // Link to the actual alert created
    
    @Column(name = "breach_id", nullable = false, length = 255)
    private String breachId;
    
    @Column(name = "database_source", length = 50)
    private String databaseSource; // "Elasticsearch" or "MongoDB"
    
    @CreationTimestamp
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
    
    // Optional: Store hash of breach content for additional duplicate detection
    @Column(name = "content_hash", length = 255)
    private String contentHash;
}