package com.threatscope.entity;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "search_history")
public class SearchHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SearchType searchType;
    
    @Column(nullable = false)
    private String query;
    
    @Column(columnDefinition = "TEXT")
    private String filters;
    
    private Long resultsCount;
    private Long executionTimeMs;
    
    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum SearchType {
        EMAIL, DOMAIN, PASSWORD, USERNAME, URL, ADVANCED, AUTO
    }
}
