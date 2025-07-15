package com.threatscopebackend.repository.mongo;

import com.threatscopebackend.document.StealerLog;
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
    
    // Case-insensitive search methods for fallback
    List<StealerLog> findByLoginContainingIgnoreCase(String login);
    List<StealerLog> findByDomainContainingIgnoreCase(String domain);
    List<StealerLog> findByUrlContainingIgnoreCase(String url);
    
    // Combined search for general queries
    List<StealerLog> findByLoginContainingIgnoreCaseOrUrlContainingIgnoreCase(String login, String url);
    
    List<StealerLog> findByUrl(String url);
    
    // For data enrichment processing

    // Statistics queries
    long count();
    
    // Count by source field (for lazy metrics)
    @Query(value = "{'source_db': ?0}", count = true)
    long countBySource(String source);
    
    @Query("{'timestamp': {'$gte': ?0}}")
    List<StealerLog> findByTimestampAfter(LocalDateTime timestamp);
    
    @Query("{'timestamp': {'$gte': ?0, '$lte': ?1}}")
    List<StealerLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
