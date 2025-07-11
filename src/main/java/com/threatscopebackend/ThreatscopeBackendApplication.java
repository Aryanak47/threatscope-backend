package com.threatscopebackend;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {
    "com.threatscopebackend"
})
@OpenAPIDefinition(
    info = @Info(
        title = "ThreatScope API",
        version = "1.0.0",
        description = "API documentation for ThreatScope - Breach Monitoring and OSINT Platform"
    )
)
@EnableAsync
@EnableRetry
public class ThreatscopeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreatscopeBackendApplication.class, args);
    }
}
