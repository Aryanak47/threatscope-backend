package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Data
@NoArgsConstructor
public class SystemSettings {
    
    public enum DataType {
        INTEGER,
        STRING,
        BOOLEAN,
        DECIMAL
    }
    
    public enum Category {
        RATE_LIMITS,
        FEATURES,
        SYSTEM,
        SUBSCRIPTION,
        SECURITY
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "setting_key", unique = true, nullable = false)
    private String key;
    
    @Column(name = "setting_value", nullable = false, length = 1000)
    private String value;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private DataType dataType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private Category category;
    
    @Column(name = "is_editable")
    private boolean editable = true;
    
    @Column(name = "default_value")
    private String defaultValue;
    
    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Helper methods to get typed values
    public Integer getIntegerValue() {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public Boolean getBooleanValue() {
        return Boolean.parseBoolean(value);
    }
    
    public BigDecimal getDecimalValue() {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    public String getStringValue() {
        return value;
    }
    
    // Constructor for easy creation
    public SystemSettings(String key, String value, String description, DataType dataType, Category category) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.dataType = dataType;
        this.category = category;
        this.defaultValue = value;
    }
}
