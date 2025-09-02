package com.threatscopebackend.entity.enums;

/**
 * Common enums used across the ThreatScope Backend application
 * This centralizes all enum definitions to avoid inconsistencies
 */
public class CommonEnums {

    /**
     * Billing cycle options for subscriptions
     */
    public enum BillingCycle {
        MONTHLY("Monthly"),
        QUARTERLY("Quarterly"), 
        ANNUALLY("Annual"),
        LIFETIME("Lifetime");
        
        private final String displayName;
        
        BillingCycle(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Plan types available in the system
     */
    public enum PlanType {
        FREE("Free"),
        BASIC("Basic"),
        PROFESSIONAL("Professional"),
        ENTERPRISE("Enterprise"),
        CUSTOM("Custom");
        
        private final String displayName;
        
        PlanType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getPlanName() {
            return this.name();
        }
    }

    /**
     * Subscription status values
     */
    public enum SubscriptionStatus {
        ACTIVE("Active"),
        TRIALING("Trial"),
        PAST_DUE("Past Due"),
        CANCELED("Canceled"),
        UNPAID("Unpaid"),
        INCOMPLETE("Incomplete"),
        INCOMPLETE_EXPIRED("Incomplete Expired"),
        PAUSED("Paused");
        
        private final String displayName;
        
        SubscriptionStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Types of monitoring available
     */
    public enum MonitorType {
        EMAIL("Email Address", "Monitor specific email addresses for data breaches"),
        DOMAIN("Domain", "Monitor entire domains for security threats"),
        USERNAME("Username", "Monitor usernames across platforms"),
        KEYWORD("Keyword", "Monitor specific keywords/terms"),
        IP_ADDRESS("IP Address", "Monitor IP addresses"),
        PHONE("Phone Number", "Monitor phone numbers"),
        ORGANIZATION("Organization", "Monitor organization names");
        
        private final String displayName;
        private final String description;
        
        MonitorType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * Monitoring check frequencies
     */
    public enum MonitorFrequency {
        REAL_TIME("Real-time", "Check immediately when new data arrives", true),
        HOURLY("Hourly", "Check every hour", true),
        DAILY("Daily", "Check once per day", false),
        WEEKLY("Weekly", "Check once per week", false);
        
        private final String displayName;
        private final String description;
        private final boolean requiresPremium;
        
        MonitorFrequency(String displayName, String description, boolean requiresPremium) {
            this.displayName = displayName;
            this.description = description;
            this.requiresPremium = requiresPremium;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean requiresPremium() {
            return requiresPremium;
        }
    }

    /**
     * Alert severity levels
     */
    public enum AlertSeverity {
        CRITICAL("Critical", "Immediate action required"),
        HIGH("High", "Urgent attention needed"),
        MEDIUM("Medium", "Important but not urgent"),
        LOW("Low", "Informational");
        
        private final String displayName;
        private final String description;
        
        AlertSeverity(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * Alert status values
     */
    public enum AlertStatus {
        NEW("New"),
        VIEWED("Viewed"),
        ACKNOWLEDGED("Acknowledged"),
        RESOLVED("Resolved"),
        DISMISSED("Dismissed");
        
        private final String displayName;
        
        AlertStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * User roles in the system
     */
    public enum UserRole {
        USER("User"),
        ADMIN("Administrator"),
        SUPER_ADMIN("Super Administrator");
        
        private final String displayName;
        
        UserRole(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Search types available
     */
    public enum SearchType {
        EMAIL("Email"),
        USERNAME("Username"),
        PHONE("Phone"),
        IP("IP Address"),
        DOMAIN("Domain"),
        HASH("Hash"),
        NAME("Name");
        
        private final String displayName;
        
        SearchType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Search modes for different types of searches
     */
    public enum SearchMode {
        EXACT("Exact Match"),
        FUZZY("Fuzzy Search"),
        WILDCARD("Wildcard Search");
        
        private final String displayName;
        
        SearchMode(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Usage event types for tracking
     */
    public enum UsageEventType {
        SEARCH("Search"),
        EXPORT("Export"),
        ALERT_GENERATED("Alert Generated"),
        MONITORING_CHECK("Monitoring Check"),
        API_CALL("API Call");
        
        private final String displayName;
        
        UsageEventType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
