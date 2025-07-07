package com.threatscopebackend.service.notification;


import com.threatscopebackend.entity.postgresql.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    @Async
    public void sendVerificationEmail(User user) {
        if (!emailEnabled) {
            log.info("Email sending is disabled. Verification email would be sent to: {}", user.getEmail());
            return;
        }

        try {
            String verificationUrl = String.format("%s/api/v1/auth/verify-email?token=%s", 
                    baseUrl, user.getEmailVerificationToken());
            
            // Prepare the evaluation context for Thymeleaf
            final Context ctx = new Context(Locale.getDefault());
            ctx.setVariable("name", user.getFirstName());
            ctx.setVariable("verificationUrl", verificationUrl);
            
            // Create HTML body using Thymeleaf
            String htmlContent = templateEngine.process("email/verify-email", ctx);
            
            sendEmail(
                    user.getEmail(),
                    "Verify your email address",
                    htmlContent,
                    true
            );
            
            log.info("Verification email sent to: {}", user.getEmail());
            
        } catch (Exception e) {
            log.error("Failed to send verification email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    @Async
    public void sendPasswordResetEmail(User user, String token) {
        if (!emailEnabled) {
            log.info("Email sending is disabled. Password reset email would be sent to: {}", user.getEmail());
            return;
        }

        try {
            String resetUrl = String.format("%s/reset-password?token=%s", baseUrl, token);
            
            // Prepare the evaluation context for Thymeleaf
            final Context ctx = new Context(Locale.getDefault());
            ctx.setVariable("name", user.getFirstName());
            ctx.setVariable("resetUrl", resetUrl);
            
            // Create HTML body using Thymeleaf
            String htmlContent = templateEngine.process("email/reset-password", ctx);
            
            sendEmail(
                    user.getEmail(),
                    "Password Reset Request",
                    htmlContent,
                    true
            );
            
            log.info("Password reset email sent to: {}", user.getEmail());
            
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    @Async
    public void sendWelcomeEmail(User user) {
        if (!emailEnabled) {
            log.info("Email sending is disabled. Welcome email would be sent to: {}", user.getEmail());
            return;
        }

        try {
            // Prepare the evaluation context for Thymeleaf
            final Context ctx = new Context(Locale.getDefault());
            ctx.setVariable("name", user.getFirstName());
            ctx.setVariable("loginUrl", baseUrl + "/login");
            
            // Create HTML body using Thymeleaf
            String htmlContent = templateEngine.process("email/welcome", ctx);
            
            sendEmail(
                    user.getEmail(),
                    "Welcome to ThreatScope!",
                    htmlContent,
                    true
            );
            
            log.info("Welcome email sent to: {}", user.getEmail());
            
        } catch (Exception e) {
            log.error("Failed to send welcome email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send welcome email", e);
        }
    }

    @Async
    public void sendEmail(String to, String subject, String content, boolean isHtml) {
        if (!emailEnabled) {
            log.info("Email sending is disabled. Email would be sent to: {} with subject: {}", to, subject);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, "UTF-8");
            
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content, isHtml);
            
            mailSender.send(mimeMessage);
            
            log.info("Email sent to: {} with subject: {}", to, subject);
            
        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
