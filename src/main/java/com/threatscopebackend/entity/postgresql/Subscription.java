package com.threatscopebackend.entity.postgresql;

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
@EqualsAndHashCode(exclude = {"user"})
public class Subscription {
    
    public enum PlanType {
        FREE,
        BASIC,
        PROFESSIONAL,
        ENTERPRISE,
        CUSTOM
    }
    
    public enum BillingCycle {
        MONTHLY,
        QUARTERLY,
        ANNUALLY
    }
    
    public enum Status {
        ACTIVE,
        TRIALING,
        PAST_DUE,
        CANCELED,
        UNPAID,
        INCOMPLETE,
        INCOMPLETE_EXPIRED,
        PAUSED
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType planType = PlanType.FREE;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
    
    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;
    
    private BigDecimal amount;
    private String currency = "USD";
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private String paymentMethodId;
    private String paymentMethodLast4;
    private String paymentMethodType;
    private String billingEmail;
    private String billingName;
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;
    private String billingCountry;
    private String taxId;
    private String couponCode;
    private BigDecimal discountAmount;
    private LocalDateTime trialStartDate;
    private LocalDateTime trialEndDate;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime canceledAt;
    private LocalDateTime cancelAtPeriodEnd;
    private LocalDateTime endedAt;
    private boolean isTaxExempt = false;
    private String notes;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // Subscription limits
    private int maxSearchesPerDay = 10;
    private int maxMonitoringItems = 5;
    private int maxExportsPerMonth = 3;
    private int maxTeamMembers = 1;
    private boolean hasApiAccess = false;
    private boolean hasPrioritySupport = false;
    private boolean hasDedicatedAccountManager = false;
    
    // Feature flags
    private boolean canExportResults = false;
    private boolean canMonitorDomains = false;
    private boolean canMonitorEmails = false;
    private boolean canMonitorKeywords = false;
    private boolean canUseApi = false;
    private boolean hasCustomBranding = false;
    
    public boolean isActive() {
        return status == Status.ACTIVE || status == Status.TRIALING;
    }
    
    public boolean isTrial() {
        return status == Status.TRIALING;
    }
    
    public boolean isTrialExpired() {
        return isTrial() && trialEndDate != null && trialEndDate.isBefore(LocalDateTime.now());
    }
    
    public boolean isCanceled() {
        return status == Status.CANCELED || status == Status.UNPAID || status == Status.INCOMPLETE_EXPIRED;
    }
}
