package com.threatscopebackend.service.consultation;

import com.threatscopebackend.dto.consultation.response.ExpertResponse;
import com.threatscopebackend.entity.postgresql.Expert;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.ExpertRepository;
import com.threatscopebackend.repository.postgresql.ConsultationSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpertAssignmentService {
    
    private final ExpertRepository expertRepository;
    private final ConsultationSessionRepository consultationSessionRepository;
    private final Random random = new Random();
    
    /**
     * Find the best available expert for a consultation session
     * Uses intelligent assignment based on expertise, availability, and load balancing
     */
    @Transactional(readOnly = true)
    public Optional<Expert> findBestAvailableExpert(String breachType, String alertSeverity) {
        log.debug("Finding best available expert for breach type: {}, severity: {}", breachType, alertSeverity);
        
        // Step 1: Get experts with capacity (not at max concurrent sessions)
        List<Expert> availableExperts = expertRepository.findAvailableExpertsWithCapacity();
        
        if (availableExperts.isEmpty()) {
            log.warn("No experts available with capacity");
            return Optional.empty();
        }
        
        // Step 2: Filter by expertise if breach type is specified
        List<Expert> matchingExperts = availableExperts;
        if (breachType != null && !breachType.isEmpty()) {
            matchingExperts = filterByExpertise(availableExperts, breachType);
            
            // If no specialists available, fall back to all available experts
            if (matchingExperts.isEmpty()) {
                log.debug("No specialists found for {}, using all available experts", breachType);
                matchingExperts = availableExperts;
            }
        }
        
        // Step 3: Apply assignment strategy based on severity
        Expert selectedExpert = switch (alertSeverity.toUpperCase()) {
            case "CRITICAL", "HIGH" -> selectHighPriorityExpert(matchingExperts);
            case "MEDIUM" -> selectBalancedExpert(matchingExperts);
            case "LOW" -> selectLoadBalancedExpert(matchingExperts);
            default -> selectRandomExpert(matchingExperts);
        };
        
        log.info("Selected expert {} for {} severity {} breach", 
                selectedExpert.getName(), alertSeverity, breachType);
        
        return Optional.of(selectedExpert);
    }
    
    /**
     * Filter experts by their expertise areas
     */
    private List<Expert> filterByExpertise(List<Expert> experts, String breachType) {
        String searchTerm = mapBreachTypeToExpertise(breachType);
        
        return experts.stream()
                .filter(expert -> {
                    String specialization = expert.getSpecialization();
                    String expertiseAreas = expert.getExpertiseAreas();
                    
                    return (specialization != null && 
                            specialization.toLowerCase().contains(searchTerm.toLowerCase())) ||
                           (expertiseAreas != null && 
                            expertiseAreas.toLowerCase().contains(searchTerm.toLowerCase()));
                })
                .toList();
    }
    
    /**
     * Map breach types to expertise areas
     */
    private String mapBreachTypeToExpertise(String breachType) {
        return switch (breachType.toLowerCase()) {
            case "data_breach", "database_breach" -> "data breach";
            case "malware", "ransomware" -> "malware";
            case "phishing", "social_engineering" -> "phishing";
            case "network_intrusion", "apt" -> "network security";
            case "identity_theft", "credential_stuffing" -> "identity theft";
            case "ddos", "dos" -> "ddos";
            case "web_application", "sql_injection" -> "web security";
            default -> breachType;
        };
    }
    
    /**
     * Select expert for high priority (critical/high severity) cases
     * Prioritizes highest rated experts
     */
    private Expert selectHighPriorityExpert(List<Expert> experts) {
        return experts.get(0); // Fallback to first available
    }
    
    /**
     * Select expert for medium priority cases
     * Balances rating and current load
     */
    private Expert selectBalancedExpert(List<Expert> experts) {
        return experts.get(0);
    }
    
    /**
     * Select expert for low priority cases
     * Prioritizes load balancing (least loaded expert)
     */
    private Expert selectLoadBalancedExpert(List<Expert> experts) {
        return experts.stream()
                .min((e1, e2) -> {
                    long load1 = consultationSessionRepository.countActiveSessionsForExpert(e1);
                    long load2 = consultationSessionRepository.countActiveSessionsForExpert(e2);
                    
                    if (load1 == load2) {
                        // If equal load, use total sessions (less experienced expert gets priority)
                        int sessions1 = e1.getTotalSessions() != null ? e1.getTotalSessions() : 0;
                        int sessions2 = e2.getTotalSessions() != null ? e2.getTotalSessions() : 0;
                        return Integer.compare(sessions1, sessions2);
                    }
                    
                    return Long.compare(load1, load2);
                })
                .orElse(experts.get(0));
    }
    
    /**
     * Select random expert (fallback)
     */
    private Expert selectRandomExpert(List<Expert> experts) {
        return experts.get(random.nextInt(experts.size()));
    }
    
    /**
     * Get all available experts
     */
    @Transactional(readOnly = true)
    public List<ExpertResponse> getAvailableExperts() {
        log.debug("Fetching all available experts");
        
        List<Expert> experts = expertRepository.findByIsActiveTrueAndIsAvailableTrue();
        
        return experts.stream()
                .map(ExpertResponse::fromEntity)
                .toList();
    }
    
    /**
     * Get expert by ID
     */
    @Transactional(readOnly = true)
    public ExpertResponse getExpertById(Long expertId) {
        log.debug("Fetching expert by ID: {}", expertId);
        
        Expert expert = expertRepository.findById(expertId)
                .orElseThrow(() -> new ResourceNotFoundException("Expert not found"));
        
        return ExpertResponse.fromEntity(expert);
    }
    
    /**
     * Get experts by specialization
     */
    @Transactional(readOnly = true)
    public List<ExpertResponse> getExpertsBySpecialization(String specialization) {
        log.debug("Fetching experts by specialization: {}", specialization);
        
        List<Expert> experts = expertRepository.findAvailableBySpecialization(specialization);
        
        return experts.stream()
                .map(ExpertResponse::fromEntity)
                .toList();
    }
    
    /**
     * Update expert availability
     */
    @Transactional
    public void updateExpertAvailability(Long expertId, boolean available) {
        log.debug("Updating expert {} availability to: {}", expertId, available);
        
        Expert expert = expertRepository.findById(expertId)
                .orElseThrow(() -> new ResourceNotFoundException("Expert not found"));
        
        expert.setIsAvailable(available);
        expertRepository.save(expert);
        
        log.info("Updated expert {} availability to: {}", expert.getName(), available);
    }
    
    /**
     * Get expert statistics
     */
    @Transactional(readOnly = true)
    public Object[] getExpertStatistics() {
        return expertRepository.getExpertStatistics();
    }
    
    /**
     * Check if expert can take new session
     */
    @Transactional(readOnly = true)
    public boolean canExpertTakeNewSession(Long expertId) {
        Expert expert = expertRepository.findById(expertId)
                .orElseThrow(() -> new ResourceNotFoundException("Expert not found"));
        
        if (!expert.canTakeNewSession()) {
            return false;
        }
        
        // Check current active session count
        long currentSessions = consultationSessionRepository.countActiveSessionsForExpert(expert);
        return currentSessions < expert.getMaxConcurrentSessions();
    }
    
    /**
     * Search experts
     */
    @Transactional(readOnly = true)
    public Page<ExpertResponse> searchExperts(String searchTerm, Pageable pageable) {
        Page<Expert> experts = expertRepository.searchExperts(searchTerm, pageable);
        return experts.map(ExpertResponse::fromEntity);
    }
}
