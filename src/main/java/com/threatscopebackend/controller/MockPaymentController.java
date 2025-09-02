package com.threatscopebackend.controller;

import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.subscription.SubscriptionService;
import com.threatscopebackend.service.core.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/mock-payment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mock Payment", description = "Mock payment system for testing plan upgrades")
public class MockPaymentController {
    
    private final SubscriptionService subscriptionService;
    private final UserService userService;
    
    @Operation(summary = "Simulate payment for plan upgrade")
    @PostMapping("/process")
    public ResponseEntity<ApiResponse<Map<String, Object>>> processPayment(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody PaymentRequest request) {
        
        try {
            log.info("🎭 Mock payment processing for user {} - Plan: {}, Amount: ${}", 
                    userPrincipal.getId(), request.planType(), request.amount());
            
            // Validate plan type
            CommonEnums.PlanType planType;
            try {
                planType = CommonEnums.PlanType.valueOf(request.planType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(
                    ApiResponse.<Map<String, Object>>error("Invalid plan type: " + request.planType())
                );
            }
            
            // Simulate payment processing delay
            Thread.sleep(1000);
            
            // Mock payment scenarios based on test data
            PaymentResult result = simulatePaymentProcessing(request);
            
            if (result.success()) {
                // Update user subscription
                User user = userService.findById(userPrincipal.getId());
                subscriptionService.upgradeSubscription(user, planType.name());
                
                log.info("✅ Mock payment successful - User {} upgraded to {}", 
                        userPrincipal.getId(), planType);
                
                Map<String, Object> response = Map.of(
                    "success", true,
                    "transactionId", result.transactionId(),
                    "planType", planType.name(),
                    "amount", request.amount(),
                    "message", "Payment processed successfully (MOCK)",
                    "newLimits", getNewLimitsForPlan(planType)
                );
                
                return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", response));
                
            } else {
                log.warn("❌ Mock payment failed - User {} - Reason: {}", 
                        userPrincipal.getId(), result.errorMessage());
                
                // Return error response with data
                return ResponseEntity.badRequest().body(
                    ApiResponse.<Map<String, Object>>builder()
                        .success(false)
                        .message("Payment failed: " + result.errorMessage())
                        .data(Map.of(
                            "success", false,
                            "errorCode", result.errorCode(),
                            "message", result.errorMessage()
                        ))
                        .timestamp(java.time.Instant.now().toString())
                        .status(400)
                        .error("Bad Request")
                        .build()
                );
            }
            
        } catch (Exception e) {
            log.error("💥 Mock payment processing error for user {}: {}", 
                    userPrincipal.getId(), e.getMessage(), e);
            
            return ResponseEntity.status(500).body(
                ApiResponse.<Map<String, Object>>error("Payment processing failed: " + e.getMessage())
            );
        }
    }
    
    @Operation(summary = "Get payment methods for testing")
    @GetMapping("/test-methods")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTestPaymentMethods() {
        
        Map<String, Object> testMethods = Map.of(
            "successfulCards", Map.of(
                "visa", "4242424242424242",
                "mastercard", "5555555555554444",
                "amex", "378282246310005"
            ),
            "failureCards", Map.of(
                "declined", "4000000000000002",
                "insufficientFunds", "4000000000009995",
                "expired", "4000000000000069"
            ),
            "testAmounts", Map.of(
                "basic", "9.99",
                "professional", "29.99", 
                "enterprise", "99.99"
            ),
            "instructions", "Use successful cards for testing upgrades, failure cards for testing error handling"
        );
        
        return ResponseEntity.ok(ApiResponse.success("Test payment methods retrieved", testMethods));
    }
    
    @Operation(summary = "Simulate subscription cancellation")
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelSubscription(
            @CurrentUser UserPrincipal userPrincipal) {
        
        try {
            log.info("🎭 Mock subscription cancellation for user {}", userPrincipal.getId());
            
            // Downgrade to free plan
            User user = userService.findById(userPrincipal.getId());
            subscriptionService.upgradeSubscription(user, "FREE");
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Subscription cancelled successfully (MOCK)",
                "planType", "FREE",
                "effectiveDate", "immediate"
            );
            
            return ResponseEntity.ok(ApiResponse.success("Subscription cancelled", response));
            
        } catch (Exception e) {
            log.error("💥 Mock cancellation error for user {}: {}", 
                    userPrincipal.getId(), e.getMessage(), e);
            
            return ResponseEntity.status(500).body(
                ApiResponse.<Map<String, Object>>error("Cancellation failed: " + e.getMessage())
            );
        }
    }
    
    private PaymentResult simulatePaymentProcessing(PaymentRequest request) {
        String cardNumber = request.cardNumber();
        String transactionId = "mock_" + UUID.randomUUID().toString().substring(0, 8);
        
        // Simulate different payment scenarios based on card number
        return switch (cardNumber) {
            case "4242424242424242" -> new PaymentResult(true, transactionId, null, null);
            case "4000000000000002" -> new PaymentResult(false, null, "DECLINED", "Your card was declined");
            case "4000000000009995" -> new PaymentResult(false, null, "INSUFFICIENT_FUNDS", "Insufficient funds");
            case "4000000000000069" -> new PaymentResult(false, null, "EXPIRED_CARD", "Your card has expired");
            default -> {
                // For any other card, simulate 90% success rate
                if (Math.random() < 0.9) {
                    yield new PaymentResult(true, transactionId, null, null);
                } else {
                    yield new PaymentResult(false, null, "PROCESSING_ERROR", "Payment processing failed");
                }
            }
        };
    }
    
    private Map<String, Object> getNewLimitsForPlan(CommonEnums.PlanType planType) {
        return switch (planType) {
            case FREE -> Map.of(
                "dailySearches", 25,
                "dailyExports", 3,
                "monitoringItems", 5,
                "apiAccess", false
            );
            case BASIC -> Map.of(
                "dailySearches", 100,
                "dailyExports", 10,
                "monitoringItems", 25,
                "apiAccess", false
            );
            case PROFESSIONAL -> Map.of(
                "dailySearches", 1200,
                "dailyExports", 50,
                "monitoringItems", 100,
                "apiAccess", true
            );
            case ENTERPRISE -> Map.of(
                "dailySearches", "unlimited",
                "dailyExports", 500,
                "monitoringItems", "unlimited",
                "apiAccess", true
            );
            default -> Map.of();
        };
    }
    
    // Request/Response records
    public record PaymentRequest(
        String planType,
        BigDecimal amount,
        String cardNumber,
        String expiryMonth,
        String expiryYear,
        String cvc,
        String billingCycle
    ) {}
    
    public record PaymentResult(
        boolean success,
        String transactionId,
        String errorCode,
        String errorMessage
    ) {}
}
