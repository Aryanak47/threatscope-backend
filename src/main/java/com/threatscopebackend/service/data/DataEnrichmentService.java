package com.threatscope.service.data;

import com.threatscope.document.StealerLog;
import com.threatscope.repository.mongo.StealerLogRepository;
import com.threatscope.service.ElasticsearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataEnrichmentService {

    private final StealerLogRepository stealerLogRepository;
    private final ElasticsearchSyncService elasticsearchSyncService;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

//    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Async
    public void processUnprocessedRecords() {
        log.info("Starting data enrichment process");
        
        List<StealerLog> unprocessedRecords = stealerLogRepository.findByProcessedFalse();
        
        if (unprocessedRecords.isEmpty()) {
            log.debug("No unprocessed records found");
            return;
        }
        
        log.info("Found {} unprocessed records to enrich", unprocessedRecords.size());
        
        for (StealerLog record : unprocessedRecords) {
            try {
                enrichRecord(record);
                record.setProcessed(true);
                record.setUpdatedAt(LocalDateTime.now());
                stealerLogRepository.save(record);
                
                elasticsearchSyncService.syncRecord(record);
                
            } catch (Exception e) {
                log.error("Failed to enrich record {}: {}", record.getId(), e.getMessage());
            }
        }
        
        log.info("Completed data enrichment for {} records", unprocessedRecords.size());
    }

    public void enrichRecord(StealerLog record) {
        if (record.getUrl() != null && record.getDomain() == null) {
            String domain = extractDomainFromUrl(record.getUrl());
            record.setDomain(domain);
        }
        
        if (record.getLogin() != null) {
            boolean isEmail = EMAIL_PATTERN.matcher(record.getLogin()).matches();
            record.setIsEmail(isEmail);
            
            if (isEmail) {
                String[] parts = record.getLogin().split("@");
                if (parts.length == 2) {
                    record.setUsername(parts[0]);
                    record.setEmailDomain(parts[1]);
                }
            } else {
                record.setUsername(record.getLogin());
            }
        }
        
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(LocalDateTime.now());
        }
        
        if (record.getTimestamp() == null) {
            record.setTimestamp(LocalDateTime.now());
        }
        
        if (record.getSeverity() == null) {
            record.setSeverity(calculateSeverity(record));
        }
    }

    private String extractDomainFromUrl(String urlString) {
        try {
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "https://" + urlString;
            }
            
            URL url = new URL(urlString);
            String host = url.getHost();
            
            if (host != null && host.startsWith("www.")) {
                host = host.substring(4);
            }
            
            return host;
        } catch (Exception e) {
            log.warn("Failed to extract domain from URL: {}", urlString);
            return null;
        }
    }

    private String calculateSeverity(StealerLog record) {
        if (record.getIsEmail() != null && record.getIsEmail()) {
            return "HIGH";
        } else if (record.getDomain() != null && isHighValueDomain(record.getDomain())) {
            return "CRITICAL";
        } else {
            return "MEDIUM";
        }
    }

    private boolean isHighValueDomain(String domain) {
        String[] highValueDomains = {
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com",
            "facebook.com", "twitter.com", "linkedin.com", "instagram.com",
            "paypal.com", "amazon.com", "apple.com", "microsoft.com",
            "google.com", "github.com", "dropbox.com"
        };
        
        for (String hvDomain : highValueDomains) {
            if (domain.equalsIgnoreCase(hvDomain)) {
                return true;
            }
        }
        
        return false;
    }

    public void enrichSpecificRecord(String recordId) {
        StealerLog record = stealerLogRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("Record not found: " + recordId));
        
        enrichRecord(record);
        record.setProcessed(true);
        record.setUpdatedAt(LocalDateTime.now());
        stealerLogRepository.save(record);
        
        elasticsearchSyncService.syncRecord(record);
        
        log.info("Manually enriched record: {}", recordId);
    }
}
