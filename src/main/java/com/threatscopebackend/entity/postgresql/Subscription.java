package com.threatscopebackend.entity.postgresql;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.threatscopebackend.entity.enums.CommonEnums;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"user", "plan"})
public class Subscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference // Prevent circular reference during JSON serialization
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private CommonEnums.PlanType planType = CommonEnums.PlanType.FREE;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CommonEnums.SubscriptionStatus status = CommonEnums.SubscriptionStatus.ACTIVE;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle")
    private CommonEnums.BillingCycle billingCycle;
    
    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    // Stripe integration fields
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;
    
    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;
    
    @Column(name = "payment_method_id")
    private String paymentMethodId;
    
    @Column(name = "payment_method_last4")
    private String paymentMethodLast4;
    
    @Column(name = "payment_method_type")
    private String paymentMethodType;
    
    // Billing information
    @Column(name = "billing_email")
    private String billingEmail;
    
    @Column(name = "billing_name")
    private String billingName;
    
    @Column(name = "billing_address_line1")
    private String billingAddressLine1;
    
    @Column(name = "billing_address_line2")
    private String billingAddressLine2;
    
    @Column(name = "billing_city")
    private String billingCity;
    
    @Column(name = "billing_state")
    private String billingState;
    
    @Column(name = "billing_postal_code")
    private String billingPostalCode;
    
    @Column(name = "billing_country")
    private String billingCountry;
    
    @Column(name = "tax_id")
    private String taxId;
    
    // Discount and coupon information
    @Column(name = "coupon_code")
    private String couponCode;
    
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;
    
    // Trial information
    @Column(name = "trial_start_date")
    private LocalDateTime trialStartDate;
    
    @Column(name = "trial_end_date")
    private LocalDateTime trialEndDate;
    
    // Billing period information
    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;
    
    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;
    
    // Cancellation information
    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;
    
    @Column(name = "cancel_at_period_end")
    private LocalDateTime cancelAtPeriodEnd;
    
    @Column(name = "ended_at")
    private LocalDateTime endedAt;
    
    @Column(name = "is_tax_exempt", nullable = false)
    private Boolean isTaxExempt = false;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Quick access limits (cached from plan for performance)
    @Column(name = "max_searches_per_day", nullable = false)
    private Integer maxSearchesPerDay = 10;
    
    @Column(name = "max_monitoring_items", nullable = false)
    private Integer maxMonitoringItems = 5;
    
    @Column(name = "max_exports_per_month", nullable = false)
    private Integer maxExportsPerMonth = 3;
    
    @Column(name = "max_team_members", nullable = false)
    private Integer maxTeamMembers = 1;
    
    // Feature flags (cached from plan for performance)
    @Column(name = "has_api_access", nullable = false)
    private Boolean hasApiAccess = false;
    
    @Column(name = "has_priority_support", nullable = false)
    private Boolean hasPrioritySupport = false;
    
    @Column(name = "has_dedicated_account_manager", nullable = false)
    private Boolean hasDedicatedAccountManager = false;
    
    @Column(name = "can_export_results", nullable = false)
    private Boolean canExportResults = false;
    
    @Column(name = "can_monitor_domains", nullable = false)
    private Boolean canMonitorDomains = false;
    
    @Column(name = "can_monitor_emails", nullable = false)
    private Boolean canMonitorEmails = false;
    
    @Column(name = "can_monitor_keywords", nullable = false)
    private Boolean canMonitorKeywords = false;
    
    @Column(name = "can_use_api", nullable = false)
    private Boolean canUseApi = false;
    
    @Column(name = "has_custom_branding", nullable = false)
    private Boolean hasCustomBranding = false;
    
    // Helper methods
    public boolean isActive() {
        return status == CommonEnums.SubscriptionStatus.ACTIVE || 
               status == CommonEnums.SubscriptionStatus.TRIALING;
    }
    
    public boolean isTrial() {
        return status == CommonEnums.SubscriptionStatus.TRIALING;
    }
    
    public boolean isTrialExpired() {
        return isTrial() && trialEndDate != null && trialEndDate.isBefore(LocalDateTime.now());
    }
    
    public boolean isCanceled() {
        return status == CommonEnums.SubscriptionStatus.CANCELED || 
               status == CommonEnums.SubscriptionStatus.UNPAID || 
               status == CommonEnums.SubscriptionStatus.INCOMPLETE_EXPIRED;
    }
    
    // Plan-based feature checks
    public boolean canCreateMonitoringItems(int currentCount) {
        return plan != null && currentCount < plan.getMaxMonitoringItems();
    }
    
    public boolean canPerformSearches(int todaysSearches) {
        return plan != null && todaysSearches < plan.getDailySearches();
    }
    
    public boolean canUseFrequency(CommonEnums.MonitorFrequency frequency) {
        return plan != null && plan.allowsFrequency(frequency);
    }
    
    public boolean hasFeature(String featureName) {
        return plan != null && plan.hasFeature(featureName);
    }
    
    // Sync limits from plan (call when plan changes)
    public void syncLimitsFromPlan() {
        if (plan != null) {
            this.maxSearchesPerDay = plan.getDailySearches();
            this.maxMonitoringItems = plan.getMaxMonitoringItems();
            this.maxExportsPerMonth = plan.getMonthlyExports();
            this.hasApiAccess = plan.getApiAccess();
            this.hasPrioritySupport = plan.getPrioritySupport();
            this.canExportResults = plan.getDailyExports() > 0;
            this.canMonitorDomains = plan.getMaxMonitoringItems() > 0;
            this.canMonitorEmails = plan.getMaxMonitoringItems() > 0;
            this.canMonitorKeywords = plan.getMaxMonitoringItems() > 0;
            this.canUseApi = plan.getApiAccess();
            this.hasCustomBranding = plan.getCustomIntegrations();
        }
    }
    
    // Check if subscription allows specific monitor type
    public boolean canUseMonitorType(CommonEnums.MonitorType monitorType) {
        return switch (monitorType) {
            case EMAIL -> canMonitorEmails;
            case DOMAIN -> canMonitorDomains;
            case KEYWORD -> canMonitorKeywords;
            case USERNAME, IP_ADDRESS, PHONE, ORGANIZATION -> plan != null && plan.getMaxMonitoringItems() > 0;
        };
    }
}
