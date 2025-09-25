package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.MonitoringConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonitoringConfigurationRepository extends JpaRepository<MonitoringConfiguration, Long> {
    
    Optional<MonitoringConfiguration> findByConfigKey(String configKey);
    
    List<MonitoringConfiguration> findByCategory(String category);
    
    List<MonitoringConfiguration> findByIsAdminConfigurableTrue();
    
    @Query("SELECT mc FROM MonitoringConfiguration mc WHERE mc.category = :category AND mc.isAdminConfigurable = true")
    List<MonitoringConfiguration> findAdminConfigurableByCategory(@Param("category") String category);
    
    @Query("SELECT mc FROM MonitoringConfiguration mc WHERE mc.configKey LIKE :keyPattern")
    List<MonitoringConfiguration> findByConfigKeyPattern(@Param("keyPattern") String keyPattern);
    
    boolean existsByConfigKey(String configKey);
}
