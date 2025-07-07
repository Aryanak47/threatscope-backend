package com.threatscopebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        // These properties will be automatically set from application.properties
        // due to the spring.mail.* prefix
        return new JavaMailSenderImpl();
    }
}