package com.threatscopebackend.entity.enums;

/**
 * Types of actions users can take on alerts
 */
public enum AlertActionType {
    SECURITY_ASSESSMENT("Security Assessment", "Request comprehensive security testing and audit", "🔍"),
    CODE_INVESTIGATION("Code Investigation", "Request code review for vulnerabilities and security issues", "🐛"),
    INCIDENT_RESPONSE("Incident Response", "Get expert help with breach response and mitigation", "🛡️"),
    EXPERT_CONSULTATION("Expert Consultation", "Schedule consultation with cybersecurity expert", "📞"),
    COMPLIANCE_CHECK("Compliance Check", "Request compliance assessment (SOC2, ISO27001, etc.)", "📋"),
    SECURITY_TRAINING("Security Training", "Request security awareness training for your team", "🔒"),
    MARK_RESOLVED("Mark as Resolved", "Mark this alert as resolved", "✅"),
    MARK_FALSE_POSITIVE("Mark as False Positive", "Mark this alert as a false positive", "❌"),
    ACKNOWLEDGE("Acknowledge", "Acknowledge that you've seen this alert", "👁️"),
    ESCALATE("Escalate", "Escalate this alert for urgent attention", "⚡");
    
    private final String displayName;
    private final String description;
    private final String icon;
    
    AlertActionType(String displayName, String description, String icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public boolean isServiceRequest() {
        return this == SECURITY_ASSESSMENT || 
               this == CODE_INVESTIGATION || 
               this == INCIDENT_RESPONSE || 
               this == EXPERT_CONSULTATION || 
               this == COMPLIANCE_CHECK || 
               this == SECURITY_TRAINING;
    }
    
    public boolean isAlertManagement() {
        return this == MARK_RESOLVED || 
               this == MARK_FALSE_POSITIVE || 
               this == ACKNOWLEDGE || 
               this == ESCALATE;
    }
}
