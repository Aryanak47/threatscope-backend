package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
    
    Optional<SystemSettings> findByKey(String key);
    
    List<SystemSettings> findByCategory(SystemSettings.Category category);
    
    List<SystemSettings> findByCategoryOrderByKey(SystemSettings.Category category);
    
    List<SystemSettings> findByEditableTrue();
    
    @Query("SELECT s FROM SystemSettings s WHERE s.key LIKE :pattern ORDER BY s.key")
    List<SystemSettings> findByKeyPattern(@Param("pattern") String pattern);
    
    boolean existsByKey(String key);
}
