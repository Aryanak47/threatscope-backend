package com.threatscope.service;

import com.threatscope.document.StealerLog;
import com.threatscope.elasticsearch.BreachDataIndex;
import com.threatscope.repository.elasticsearch.BreachDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchSyncService {

    private final BreachDataRepository breachDataRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    
    @Async
    @Retryable(retryFor = { Exception.class },
                maxAttempts = 3,
                backoff = @Backoff(delay = 1000, multiplier = 2))
    public void syncRecord(StealerLog stealerLog) {
        try {
            BreachDataIndex index = convertToIndex(stealerLog);
            breachDataRepository.save(index);
            log.debug("Successfully synced record to Elasticsearch: {}", stealerLog.getId());
        } catch (Exception e) {
            log.error("Failed to sync record to Elasticsearch: {}", stealerLog.getId(), e);
            throw e; // Will trigger retry
        }
    }
    
    public void deleteRecord(String id) {
        try {
            breachDataRepository.deleteById(id);
            log.debug("Successfully deleted record from Elasticsearch: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete record from Elasticsearch: {}", id, e);
            throw e;
        }
    }
    
    @Async
    public void syncAllRecords() {
        // Implementation to sync all records from MongoDB to Elasticsearch
        // This would typically be used for initial sync or recovery
    }
    
    private BreachDataIndex convertToIndex(StealerLog stealerLog) {
        BreachDataIndex index = new BreachDataIndex();
        index.setId(stealerLog.getId());
        index.setLogin(stealerLog.getLogin());
        index.setPassword(stealerLog.getPassword());
        index.setUrl(stealerLog.getUrl());
        index.setTimestamp(stealerLog.getTimestamp() != null ? 
                          stealerLog.getTimestamp() : LocalDateTime.now());
        
        // Copy metadata or create a new one
        if (stealerLog.getMetadata() != null) {
            index.setMetadata(new HashMap<>(stealerLog.getMetadata()));
        } else {
            index.setMetadata(new HashMap<>());
        }
        
        // Add derived fields to metadata
        index.getMetadata().put("domain", stealerLog.getDomain());
        index.getMetadata().put("isEmail", stealerLog.getIsEmail());
        index.getMetadata().put("username", stealerLog.getUsername());
        index.getMetadata().put("emailDomain", stealerLog.getEmailDomain());
        
        return index;
    }
}
