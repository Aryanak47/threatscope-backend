package com.threatscopebackend.entity.postgresql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "experts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "specialization")
    private String specialization; // "Data Breaches", "Malware", "Network Security"
    
    @Column(name = "expertise_areas", columnDefinition = "TEXT")
    private String expertiseAreas; // JSON array of expertise areas
    
    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;
    
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;
    
    @Column(name = "certifications", columnDefinition = "TEXT")
    private String certifications; // JSON array
    
    @Column(name = "languages", columnDefinition = "TEXT") 
    private String languages; // JSON array
    
    @Column(name = "timezone")
    private String timezone = "UTC";
    
    @Column(name = "max_concurrent_sessions")
    private Integer maxConcurrentSessions = 3;
    
    @Column(name = "total_sessions")
    private Integer totalSessions = 0;
    
    @Column(name = "completed_sessions")
    private Integer completedSessions = 0;

    
    @Column(name = "total_revenue", precision = 12, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at") 
    private LocalDateTime updatedAt;
    
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;
    
    @OneToMany(mappedBy = "expert", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<ConsultationSession> consultationSessions;
    
    // Helper methods
    public boolean canTakeNewSession() {
        return isActive && isAvailable;
    }
    
    public void recordSession() {
        this.totalSessions = (this.totalSessions != null ? this.totalSessions : 0) + 1;
    }
    
    public void recordCompletedSession(BigDecimal sessionRevenue) {
        this.completedSessions = (this.completedSessions != null ? this.completedSessions : 0) + 1;
        if (sessionRevenue != null) {
            this.totalRevenue = (this.totalRevenue != null ? this.totalRevenue : BigDecimal.ZERO)
                .add(sessionRevenue);
        }
    }
    

    
    @PrePersist
    protected void onCreate() {
        if (lastActiveAt == null) {
            lastActiveAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastActiveAt = LocalDateTime.now();
    }
}
