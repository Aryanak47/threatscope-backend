package com.threatscopebackend.config;

import com.threatscopebackend.service.datasource.DataSourceService;
import com.threatscopebackend.service.datasource.InternalDataSource;
import com.threatscopebackend.service.datasource.BreachVipDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration to ensure data sources are properly registered
 * This provides a fallback if Spring's auto-discovery doesn't work
 */
@Configuration
@Slf4j
public class DataSourceConfiguration {
    
    /**
     * Explicitly define the list of data sources if auto-injection fails
     * This ensures DataSourceManager always has the correct implementations
     */
    @Bean(name = "dataSourceServiceList")
    public List<DataSourceService> dataSourceServices(
            InternalDataSource internalDataSource,
            BreachVipDataSource breachVipDataSource) {
        
        log.info("Manually configuring data sources list:");
        log.info("  - InternalDataSource: {}", internalDataSource.getSourceName());
        log.info("  - BreachVipDataSource: {}", breachVipDataSource.getSourceName());
        
        return List.of(internalDataSource, breachVipDataSource);
    }
}