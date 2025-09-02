package com.threatscopebackend.entity.postgresql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.threatscopebackend.entity.enums.CommonEnums;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "consultation_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationPlan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name; // "Basic Consultation", "Professional", "Enterprise"
    
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    @Column(name = "session_duration_minutes", nullable = false)
    private Integer sessionDurationMinutes;
    
    @Column(name = "features", columnDefinition = "TEXT")
    private String features; // JSON array of features
    
    @Column(name = "deliverables", columnDefinition = "TEXT") 
    private String deliverables; // JSON array of what's included
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_popular", nullable = false)
    private Boolean isPopular = false;
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Column(name = "max_sessions_per_month")
    private Integer maxSessionsPerMonth; // For recurring plans
    
    @Column(name = "includes_follow_up", nullable = false)
    private Boolean includesFollowUp = false;
    
    @Column(name = "follow_up_days")
    private Integer followUpDays; // Days of follow-up support
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Helper methods
    public boolean isAvailableForPurchase() {
        return isActive;
    }
    
    public String getFormattedPrice() {
        return String.format("$%.2f", price);
    }
    
    public String getDurationDisplay() {
        int hours = sessionDurationMinutes / 60;
        int minutes = sessionDurationMinutes % 60;
        
        if (hours > 0 && minutes > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh", hours);
        } else {
            return String.format("%dm", minutes);
        }
    }
}
