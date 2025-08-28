package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.Expert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpertRepository extends JpaRepository<Expert, Long> {
    
    // Find active experts
    List<Expert> findByIsActiveTrue();
    
    // Find available experts
    List<Expert> findByIsActiveTrueAndIsAvailableTrue();
    
    // Find by specialization
    List<Expert> findBySpecializationContainingIgnoreCase(String specialization);
    
    // Find available experts by specialization
    @Query("SELECT e FROM Expert e WHERE e.isActive = true AND e.isAvailable = true " +
           "AND LOWER(e.specialization) LIKE LOWER(CONCAT('%', :specialization, '%'))")
    List<Expert> findAvailableBySpecialization(@Param("specialization") String specialization);
    
    // Find available experts by expertise area
    @Query("SELECT e FROM Expert e WHERE e.isActive = true AND e.isAvailable = true " +
           "AND LOWER(e.expertiseAreas) LIKE LOWER(CONCAT('%', :expertiseArea, '%'))")
    List<Expert> findAvailableByExpertiseArea(@Param("expertiseArea") String expertiseArea);
    
    // Find expert by email
    Optional<Expert> findByEmail(String email);
    
    // Check if expert exists by email
    boolean existsByEmail(String email);
    
    // Get experts with pagination and sorting
    Page<Expert> findAll(Pageable pageable);
    
    // Get active experts with pagination
    Page<Expert> findByIsActiveTrue(Pageable pageable);
    

    
    // Count available experts
    long countByIsActiveTrueAndIsAvailableTrue();
    
    // Count total active experts
    long countByIsActiveTrue();

    
    // Find experts with current session count less than max
    @Query("SELECT e FROM Expert e WHERE e.isActive = true AND e.isAvailable = true " +
           "AND (SELECT COUNT(cs) FROM ConsultationSession cs WHERE cs.expert = e " +
           "AND cs.status IN ('ASSIGNED', 'ACTIVE')) < e.maxConcurrentSessions")
    List<Expert> findAvailableExpertsWithCapacity();
    
    // Get expert statistics
    @Query("SELECT COUNT(e), SUM(e.totalSessions) FROM Expert e WHERE e.isActive = true")
    Object[] getExpertStatistics();

    // Search experts by name or specialization
    @Query("SELECT e FROM Expert e WHERE e.isActive = true AND " +
           "(LOWER(e.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.specialization) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.expertiseAreas) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Expert> searchExperts(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    // ===== ADMIN CONSULTATION METHODS =====
    
    // Find available experts only
    List<Expert> findByIsAvailableTrue();
    
    // Find by specialization and available
    List<Expert> findBySpecializationAndIsAvailableTrue(String specialization);
    
    // Count available experts
    Long countByIsAvailableTrue();
}
