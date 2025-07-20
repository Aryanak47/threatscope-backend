package com.threatscopebackend.config;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.Plan;
import com.threatscopebackend.repository.postgresql.PlanRepository;
import com.threatscopebackend.service.admin.MonitoringConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultDataInitializer implements CommandLineRunner {
    
    private final PlanRepository planRepository;
    private final MonitoringConfigurationService configService;
    
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Initializing default data...");
        
        initializeDefaultPlans();
        configService.initializeDefaultConfigurations();
        
        log.info("Default data initialization completed");
    }
    
    private void initializeDefaultPlans() {
        // Check if plans already exist
        if (planRepository.count() > 0) {
            log.info("Plans already exist, skipping initialization");
            return;
        }
        
        log.info("Creating default subscription plans");
        
        // FREE PLAN
        Plan freePlan = Plan.builder()
            .planType(CommonEnums.PlanType.FREE)
            .displayName("Free Plan")
            .description("Basic search functionality with limited features")
            .price(BigDecimal.ZERO)
            .currency("USD")
            .billingCycle(CommonEnums.BillingCycle.MONTHLY)
            .dailySearches(5)
            .monthlySearches(150)
            .maxMonitoringItems(0) // No monitoring for free users
            .monitoringFrequencies("[]") // No monitoring frequencies allowed
            .maxAlertsPerDay(0)
            .alertRetentionDays(0)
            .dailyExports(0)
            .monthlyExports(0)
            .apiAccess(false)
            .realTimeMonitoring(false)
            .emailAlerts(false)
            .inAppAlerts(false)
            .webhookAlerts(false)
            .prioritySupport(false)
            .customIntegrations(false)
            .advancedAnalytics(false)
            .isActive(true)
            .isPublic(true)
            .sortOrder(1)
            .build();
        
        // BASIC PLAN
        Plan basicPlan = Plan.builder()
            .planType(CommonEnums.PlanType.BASIC)
            .displayName("Basic Plan")
            .description("Perfect for individuals who need basic monitoring capabilities")
            .price(new BigDecimal("9.99"))
            .currency("USD")
            .billingCycle(CommonEnums.BillingCycle.MONTHLY)
            .dailySearches(100)
            .monthlySearches(3000)
            .maxMonitoringItems(5)
            .monitoringFrequencies("[\"DAILY\", \"WEEKLY\"]")
            .maxAlertsPerDay(50)
            .alertRetentionDays(30)
            .dailyExports(5)
            .monthlyExports(150)
            .apiAccess(false)
            .realTimeMonitoring(false)
            .emailAlerts(true)
            .inAppAlerts(true)
            .webhookAlerts(false)
            .prioritySupport(false)
            .customIntegrations(false)
            .advancedAnalytics(false)
            .isActive(true)
            .isPublic(true)
            .sortOrder(2)
            .build();
        
        // PROFESSIONAL PLAN
        Plan professionalPlan = Plan.builder()
            .planType(CommonEnums.PlanType.PROFESSIONAL)
            .displayName("Professional Plan")
            .description("Advanced features for security professionals and small teams")
            .price(new BigDecimal("29.99"))
            .currency("USD")
            .billingCycle(CommonEnums.BillingCycle.MONTHLY)
            .dailySearches(1000)
            .monthlySearches(30000)
            .maxMonitoringItems(25)
            .monitoringFrequencies("[\"REAL_TIME\", \"HOURLY\", \"DAILY\", \"WEEKLY\"]")
            .maxAlertsPerDay(200)
            .alertRetentionDays(90)
            .dailyExports(25)
            .monthlyExports(750)
            .apiAccess(true)
            .realTimeMonitoring(true)
            .emailAlerts(true)
            .inAppAlerts(true)
            .webhookAlerts(true)
            .prioritySupport(true)
            .customIntegrations(false)
            .advancedAnalytics(true)
            .isActive(true)
            .isPublic(true)
            .sortOrder(3)
            .build();
        
        // ENTERPRISE PLAN
        Plan enterprisePlan = Plan.builder()
            .planType(CommonEnums.PlanType.ENTERPRISE)
            .displayName("Enterprise Plan")
            .description("Unlimited features for large organizations and enterprises")
            .price(new BigDecimal("99.99"))
            .currency("USD")
            .billingCycle(CommonEnums.BillingCycle.MONTHLY)
            .dailySearches(10000) // Effectively unlimited
            .monthlySearches(300000)
            .maxMonitoringItems(100) // Effectively unlimited
            .monitoringFrequencies("[\"REAL_TIME\", \"HOURLY\", \"DAILY\", \"WEEKLY\"]")
            .maxAlertsPerDay(1000)
            .alertRetentionDays(365)
            .dailyExports(100)
            .monthlyExports(3000)
            .apiAccess(true)
            .realTimeMonitoring(true)
            .emailAlerts(true)
            .inAppAlerts(true)
            .webhookAlerts(true)
            .prioritySupport(true)
            .customIntegrations(true)
            .advancedAnalytics(true)
            .isActive(true)
            .isPublic(true)
            .sortOrder(4)
            .build();
        
        // Save all plans
        List<Plan> plans = List.of(freePlan, basicPlan, professionalPlan, enterprisePlan);
        planRepository.saveAll(plans);
        
        log.info("Created {} default subscription plans", plans.size());
    }
}
