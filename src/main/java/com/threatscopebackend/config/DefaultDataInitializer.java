package com.threatscopebackend.config;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.Plan;
import com.threatscopebackend.entity.postgresql.ConsultationPlan;
import com.threatscopebackend.entity.postgresql.Expert;
import com.threatscopebackend.repository.postgresql.PlanRepository;
import com.threatscopebackend.repository.postgresql.ConsultationPlanRepository;
import com.threatscopebackend.repository.postgresql.ExpertRepository;
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
    private final ConsultationPlanRepository consultationPlanRepository;
    private final ExpertRepository expertRepository;
    private final MonitoringConfigurationService configService;
    
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Initializing default data...");
        
        initializeDefaultPlans();
        initializeConsultationPlans();
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
    
    private void initializeConsultationPlans() {
        // Check if consultation plans already exist
        if (consultationPlanRepository.count() > 0) {
            log.info("Consultation plans already exist, skipping initialization");
            return;
        }
        
        log.info("Creating default consultation plans");
        
        // BASIC CONSULTATION
        ConsultationPlan basicConsultation = ConsultationPlan.builder()
            .name("basic")
            .displayName("Basic Consultation")
            .description("Get expert guidance on your security alert with personalized recommendations")
            .price(new BigDecimal("29.99"))
            .currency("USD")
            .sessionDurationMinutes(30)
            .features("[\"Expert analysis of the breach\", \"30-minute chat session\", \"Basic remediation steps\", \"Password change guidance\"]")
            .deliverables("[\"Security assessment\", \"Action plan\", \"Best practices guide\"]")
            .isActive(true)
            .isPopular(false)
            .sortOrder(1)
            .includesFollowUp(false)
            .build();
        
        // PROFESSIONAL CONSULTATION
        ConsultationPlan professionalConsultation = ConsultationPlan.builder()
            .name("professional")
            .displayName("Professional Consultation")
            .description("Comprehensive security analysis with detailed action plan and follow-up support")
            .price(new BigDecimal("79.99"))
            .currency("USD")
            .sessionDurationMinutes(60)
            .features("[\"Everything in Basic\", \"60-minute chat session\", \"Detailed security assessment\", \"Custom action plan document\", \"Follow-up monitoring setup\"]")
            .deliverables("[\"Detailed security report\", \"Custom remediation plan\", \"Monitoring recommendations\", \"Follow-up support\"]")
            .isActive(true)
            .isPopular(true)
            .sortOrder(2)
            .includesFollowUp(true)
            .followUpDays(7)
            .build();
        
        // ENTERPRISE CONSULTATION
        ConsultationPlan enterpriseConsultation = ConsultationPlan.builder()
            .name("enterprise")
            .displayName("Enterprise Consultation")
            .description("Complete security audit with executive briefing and ongoing support")
            .price(new BigDecimal("199.99"))
            .currency("USD")
            .sessionDurationMinutes(90)
            .features("[\"Everything in Professional\", \"90-minute chat session\", \"Complete security audit\", \"Executive-level report\", \"30-day follow-up support\", \"Integration recommendations\"]")
            .deliverables("[\"Executive security briefing\", \"Complete audit report\", \"Strategic recommendations\", \"Implementation roadmap\", \"30-day support\"]")
            .isActive(true)
            .isPopular(false)
            .sortOrder(3)
            .includesFollowUp(true)
            .followUpDays(30)
            .build();
        
        List<ConsultationPlan> consultationPlans = List.of(basicConsultation, professionalConsultation, enterpriseConsultation);
        consultationPlanRepository.saveAll(consultationPlans);
        
        log.info("Created {} default consultation plans", consultationPlans.size());
    }
    

}
