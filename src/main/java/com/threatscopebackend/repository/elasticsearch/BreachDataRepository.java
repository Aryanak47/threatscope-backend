package com.threatscopebackend.repository.elasticsearch;

import com.threatscopebackend.elasticsearch.BreachDataIndex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface BreachDataRepository extends ElasticsearchRepository<BreachDataIndex, String> {
    
    Page<BreachDataIndex> findByLogin(String login, Pageable pageable);
    Page<BreachDataIndex> findByUrl(String url, Pageable pageable);
    Page<BreachDataIndex> findByPassword(String password, Pageable pageable);
    Page<BreachDataIndex> findByTimestamp(LocalDateTime timestamp, Pageable pageable);
    Page<BreachDataIndex> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<BreachDataIndex> findByTimestampAfter(LocalDateTime timestamp, Pageable pageable);
    
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"login\", \"url\", \"password\"]}}")
    Page<BreachDataIndex> multiFieldSearch(String query, Pageable pageable);
    
    @Query("{\"term\": {\"login\": \"?0\"}}")
    Page<BreachDataIndex> findByLoginExact(String login, Pageable pageable);
    
    @Query("{\"match\": {\"url\": \"?0\"}}")
    Page<BreachDataIndex> findByUrlMatch(String url, Pageable pageable);
    
    @Query("{\"match_phrase\": {\"url\": \"?0\"}}")
    Page<BreachDataIndex> findByUrlPhrase(String url, Pageable pageable);
    
    @Query("{\"wildcard\": {\"url\": \"*?0*\"}}")
    Page<BreachDataIndex> findByUrlWildcard(String urlPattern, Pageable pageable);
    
    @Query("{\"match\": {\"password\": \"?0\"}}")
    Page<BreachDataIndex> findByPasswordMatch(String password, Pageable pageable);
    
    @Query("{\"bool\": {\"must\": [{\"term\": {\"login\": \"?0\"}}, {\"match\": {\"url\": \"?1\"}}]}}")
    Page<BreachDataIndex> findByLoginAndUrl(String login, String url, Pageable pageable);
    
    @Query("{\"exists\": {\"field\": \"metadata\"}}")
    Page<BreachDataIndex> findByMetadataExists(Pageable pageable);
    
    @Query("{\"bool\": {\"must\": [{\"match\": {\"metadata.?0\": \"?1\"}}]}}")
    Page<BreachDataIndex> findByMetadataField(String fieldName, String fieldValue, Pageable pageable);
    
    @Query("{\"range\": {\"timestamp\": {\"gte\": \"?0\"}}}")
    Page<BreachDataIndex> findRecordsSince(LocalDateTime since, Pageable pageable);
    
    @Query("{\"range\": {\"timestamp\": {\"gte\": \"?0\", \"lte\": \"?1\"}}}")
    Page<BreachDataIndex> findRecordsBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    @Query("{\"range\": {\"timestamp\": {\"gte\": \"now-1d\"}}}")
    Page<BreachDataIndex> findRecordsLast24Hours(Pageable pageable);
    
    @Query("{\"range\": {\"timestamp\": {\"gte\": \"now-7d\"}}}")
    Page<BreachDataIndex> findRecordsLastWeek(Pageable pageable);
    
    @Query("{\"range\": {\"timestamp\": {\"gte\": \"now-30d\"}}}")
    Page<BreachDataIndex> findRecordsLastMonth(Pageable pageable);
    
    @Query("{\"regexp\": {\"login\": \".*@.*\"}}")
    Page<BreachDataIndex> findEmailLogins(Pageable pageable);
    
    @Query("{\"wildcard\": {\"login\": \"*@?0\"}}")
    Page<BreachDataIndex> findByEmailDomain(String domain, Pageable pageable);
    
    @Query("{\"wildcard\": {\"url\": \"*?0*\"}}")
    Page<BreachDataIndex> findByUrlContainsDomain(String domain, Pageable pageable);
    
    long countByLogin(String login);
    long countByUrl(String url);
    long countByTimestampAfter(LocalDateTime timestamp);
    long countByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("{\"bool\": {\"must\": [{\"term\": {\"login\": \"?0\"}}, {\"range\": {\"timestamp\": {\"gte\": \"?1\"}}}]}}")
    Page<BreachDataIndex> findNewBreachesForLogin(String login, LocalDateTime since, Pageable pageable);
    
    @Query("{\"bool\": {\"must\": [{\"wildcard\": {\"url\": \"*?0*\"}}, {\"range\": {\"timestamp\": {\"gte\": \"?1\"}}}]}}")
    Page<BreachDataIndex> findNewBreachesForDomain(String domain, LocalDateTime since, Pageable pageable);
    
    @Query("{\"term\": {\"login\": \"?0\"}}")
    Page<BreachDataIndex> findAllRecordsForLogin(String login, Pageable pageable);
    
    @Query("{\"match\": {\"url\": \"?0\"}}")
    Page<BreachDataIndex> findAllRecordsForUrl(String url, Pageable pageable);
}
