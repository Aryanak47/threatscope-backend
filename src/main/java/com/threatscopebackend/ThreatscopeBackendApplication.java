package com.threatscopebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {
    "com.threatscopebackend"
})
@EnableAsync
@EnableRetry
public class ThreatscopeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreatscopeBackendApplication.class, args);
    }

}
