package com.threatscopebackend.service.notification;

import com.threatscopebackend.entity.postgresql.BreachAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final JavaMailSender mailSender;
    
    /**
     * Send email notification for a breach alert
     */
    public void sendAlertEmail(BreachAlert alert) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(alert.getUser().getEmail());
            message.setSubject(buildEmailSubject(alert));
            message.setText(buildEmailBody(alert));
            message.setFrom("alerts@threatscope.com");
            
            mailSender.send(message);
            log.info("Sent email alert notification to: {}", alert.getUser().getEmail());
            
        } catch (Exception e) {
            log.error("Failed to send email notification for alert {}: {}", alert.getId(), e.getMessage());
            throw new RuntimeException("Email notification failed", e);
        }
    }
    
    /**
     * Send in-app notification (placeholder for WebSocket/SSE implementation)
     */
    public void sendInAppNotification(BreachAlert alert) {
        // TODO: Implement WebSocket or Server-Sent Events for real-time notifications
        log.info("In-app notification sent for alert {} to user {}", alert.getId(), alert.getUser().getId());
    }
    
    private String buildEmailSubject(BreachAlert alert) {
        String severityPrefix = switch (alert.getSeverity()) {
            case CRITICAL -> "🔴 CRITICAL";
            case HIGH -> "🟠 HIGH";
            case MEDIUM -> "🟡 MEDIUM";
            case LOW -> "🟢 LOW";
        };
        
        return String.format("%s - %s", severityPrefix, alert.getTitle());
    }
    
    private String buildEmailBody(BreachAlert alert) {
        StringBuilder body = new StringBuilder();
        
        body.append("Dear ").append(alert.getUser().getFirstName()).append(",\n\n");
        
        body.append("A new security alert has been detected:\n\n");
        
        body.append("Alert Details:\n");
        body.append("- Title: ").append(alert.getTitle()).append("\n");
        body.append("- Severity: ").append(alert.getSeverity()).append("\n");
        body.append("- Source: ").append(alert.getBreachSource()).append("\n");
        
        if (alert.getBreachDate() != null) {
            body.append("- Breach Date: ").append(alert.getBreachDate()).append("\n");
        }
        
        if (alert.getMonitoringItem() != null) {
            body.append("- Monitoring Item: ").append(alert.getMonitoringItem().getMonitorName()).append("\n");
            body.append("- Monitor Type: ").append(alert.getMonitoringItem().getMonitorType()).append("\n");
        }
        
        body.append("\nDescription:\n");
        body.append(alert.getDescription()).append("\n\n");
        
        body.append("Please log into ThreatScope to view more details and take appropriate action.\n\n");
        
        body.append("Best regards,\n");
        body.append("ThreatScope Security Team");
        
        return body.toString();
    }
}
