package com.threatscopebackend.repository.mongo;

import com.threatscope.document.StealerLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface StealerLogRepository extends MongoRepository<StealerLog, String> {
    
    // Primary method: Find documents by MongoDB IDs (used after Elasticsearch search)
    List<StealerLog> findByIdIn(Set<String> ids);
    
    // Check for existence using your unique constraint
    Optional<StealerLog> findByLoginAndPasswordAndUrl(String login, String password, String url);
    
    boolean existsByLoginAndPasswordAndUrl(String login, String password, String url);
    
    // Basic search methods for fallback when ES is down
    List<StealerLog> findByLogin(String login);
    
    List<StealerLog> findByUrl(String url);
    
    // For data enrichment processing
    List<StealerLog> findByProcessedFalse();
    
    // Statistics queries
    long count();
    
    @Query("{'timestamp': {'$gte': ?0}}")
    List<StealerLog> findByTimestampAfter(LocalDateTime timestamp);
    
    @Query("{'timestamp': {'$gte': ?0, '$lte': ?1}}")
    List<StealerLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
