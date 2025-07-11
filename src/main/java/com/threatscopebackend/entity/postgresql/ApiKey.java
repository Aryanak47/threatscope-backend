package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_keys")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"user"})
public class ApiKey {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;
    
    @Column(name = "key_prefix", length = 8, nullable = false)
    private String keyPrefix;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "ip_whitelist", columnDefinition = "TEXT")
    private String ipWhitelist; // JSON array of allowed IPs/CIDR ranges
    
    @Column(name = "referer_whitelist", columnDefinition = "TEXT")
    private String refererWhitelist; // JSON array of allowed referrers
    
    @Column(name = "rate_limit_per_minute")
    private Integer rateLimitPerMinute = 60;
    
    @Column(name = "rate_limit_per_day")
    private Integer rateLimitPerDay = 5000;
    
    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions; // JSON array of permissions
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    @Column(name = "revoke_reason")
    private String revokeReason;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "created_by_ip")
    private String createdByIp;
    
    @Column(name = "last_used_by_ip")
    private String lastUsedByIp;
    
    @Column(name = "usage_count")
    private Long usageCount = 0L;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON string for additional data
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    public boolean isRevoked() {
        return revokedAt != null;
    }
    
    public boolean isValid() {
        return isActive && !isRevoked() && !isExpired();
    }
}
