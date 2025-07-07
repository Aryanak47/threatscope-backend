package com.threatscope.service.monitoring;

import com.threatscope.dto.SystemHealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemHealthService {
    
    private final DataSource dataSource;
    private final MongoTemplate mongoTemplate;
    private final ElasticsearchOperations elasticsearchOperations;
    private final RedisTemplate<String, String> redisTemplate;
    
    public String getOverallHealth() {
        try {
            boolean pgHealthy = checkPostgreSQLHealth();
            boolean mongoHealthy = checkMongoDBHealth();
            boolean esHealthy = checkElasticsearchHealth();
            boolean redisHealthy = checkRedisHealth();
            
            if (pgHealthy && mongoHealthy && esHealthy && redisHealthy) {
                return "HEALTHY";
            } else if (pgHealthy && mongoHealthy) {
                return "DEGRADED";
            } else {
                return "DOWN";
            }
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
            return "UNKNOWN";
        }
    }
    
    public SystemHealthResponse getDetailedHealthMetrics() {
        Map<String, Object> components = new HashMap<>();
        
        components.put("postgresql", getPostgreSQLStatus());
        components.put("mongodb", getMongoDBStatus());
        components.put("elasticsearch", getElasticsearchStatus());
        components.put("redis", getRedisStatus());
        
        return SystemHealthResponse.builder()
                .overall(getOverallHealth())
                .components(components)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }
    
    private boolean checkPostgreSQLHealth() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (Exception e) {
            log.warn("PostgreSQL health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean checkMongoDBHealth() {
        try {
            mongoTemplate.getCollection("test");
            return true;
        } catch (Exception e) {
            log.warn("MongoDB health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean checkElasticsearchHealth() {
        try {
            return elasticsearchOperations.indexOps(com.threatscope.elasticsearch.BreachDataIndex.class).exists();
        } catch (Exception e) {
            log.warn("Elasticsearch health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean checkRedisHealth() {
        try {
            redisTemplate.opsForValue().set("health-check", "ok");
            return "ok".equals(redisTemplate.opsForValue().get("health-check"));
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private Map<String, Object> getPostgreSQLStatus() {
        Map<String, Object> status = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            status.put("status", "UP");
            status.put("details", Map.of(
                "database", connection.getCatalog(),
                "url", connection.getMetaData().getURL()
            ));
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }
    
    private Map<String, Object> getMongoDBStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            mongoTemplate.getCollection("test");
            status.put("status", "UP");
            status.put("details", Map.of("database", "connected"));
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }
    
    private Map<String, Object> getElasticsearchStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            boolean exists = elasticsearchOperations.indexOps(com.threatscope.elasticsearch.BreachDataIndex.class).exists();
            status.put("status", exists ? "UP" : "DOWN");
            status.put("details", Map.of("index_exists", exists));
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }
    
    private Map<String, Object> getRedisStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            redisTemplate.opsForValue().set("health-check", "ok");
            status.put("status", "UP");
            status.put("details", Map.of("connection", "active"));
        } catch (Exception e) {
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }
        return status;
    }
}
