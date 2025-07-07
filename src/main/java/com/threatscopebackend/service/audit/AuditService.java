package com.threatscopebackend.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatscope.entity.postgresql.AuditLog;
import com.threatscope.repository.postgresql.AuditLogRepository;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.repository.postgresql.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

//    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    @Transactional
    public void logUserAction(Long userId, AuditLog.AuditAction action, String ipAddress) {
        logUserAction(userId, action, null, null, null, null, ipAddress, null, null);
    }

    @Async
    @Transactional
    public void logUserAction(Long userId, AuditLog.AuditAction action, String resourceType, 
                             String resourceId, String ipAddress) {
        logUserAction(userId, action, resourceType, resourceId, null, null, ipAddress, null, null);
    }

    @Async
    @Transactional
    public void logUserAction(Long userId, AuditLog.AuditAction action, String resourceType, 
                             String resourceId, Map<String, Object> oldValues, 
                             Map<String, Object> newValues, String ipAddress, 
                             String userAgent, String errorMessage) {
        try {
            // Set user
            User user = new User();
            user.setId(userId);
        } catch (Exception e) {
            log.error("Failed to log audit action: {}", e.getMessage(), e);
        }
    }

    @Async
    @Transactional
    public void logFailedLogin(String email, String ipAddress, String userAgent, String errorMessage) {
        try {
            // Find user by email
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                logUserAction(
                    userOpt.get().getId(),
                    AuditLog.AuditAction.USER_LOGIN,
                    "User",
                    userOpt.get().getId().toString(),
                    null,
                    null,
                    ipAddress,
                    userAgent,
                    errorMessage
                );
            } else {
                // Log failed login attempt for non-existent user
                log.warn("Failed login attempt for non-existent user: {}", email);
            }
        } catch (Exception e) {
            log.error("Failed to log failed login attempt: {}", e.getMessage(), e);
        }
    }

    private String convertMapToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert map to JSON: {}", e.getMessage(), e);
            return "{}";
        }
    }
    
    // Additional helper methods for specific audit log scenarios
    
    @Async
    public void logSearchPerformed(Long userId, String query, String searchType, 
                                  String ipAddress, String userAgent) {
        logUserAction(
            userId,
            AuditLog.AuditAction.SEARCH_PERFORMED,
            "Search",
            null,
            Map.of("type", searchType, "query", query),
            null,
            ipAddress,
            userAgent,
            null
        );
    }
    
    @Async
    public void logExportGenerated(Long userId, String exportType, String resourceId, 
                                  String ipAddress) {
        logUserAction(
            userId,
            AuditLog.AuditAction.EXPORT_GENERATED,
            "Export",
            resourceId,
            null,
            Map.of("type", exportType),
            ipAddress,
            null,
            null
        );
    }
    
    @Async
    public void logApiKeyAction(Long userId, AuditLog.AuditAction action, String keyId, 
                               String keyName, String ipAddress) {
        logUserAction(
            userId,
            action,
            "ApiKey",
            keyId,
            null,
            Map.of("keyName", keyName),
            ipAddress,
            null,
            null
        );
    }
}
