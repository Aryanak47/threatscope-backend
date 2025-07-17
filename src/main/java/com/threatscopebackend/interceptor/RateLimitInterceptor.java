package com.threatscopebackend.interceptor;

import com.threatscopebackend.service.core.UsageService;
import com.threatscopebackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final UsageService usageService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        // Only apply rate limiting to search endpoints
        String requestURI = request.getRequestURI();
        if (!isSearchEndpoint(requestURI)) {
            return true; // Continue with request
        }
        
        // Check if user is authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() 
            && !(authentication.getPrincipal() instanceof String)) {
            
            // Authenticated user - check their usage limits
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            
            if (!usageService.canPerformAction(userPrincipal, UsageService.UsageType.SEARCH)) {
                log.warn("Rate limit exceeded for authenticated user: {}", userPrincipal.getId());
                sendRateLimitResponse(response, "Search limit exceeded. Please upgrade your plan or try again tomorrow.");
                return false;
            }
            
            // Record the usage
            usageService.recordUsage(userPrincipal, UsageService.UsageType.SEARCH);
            log.debug("Recorded search usage for user: {}", userPrincipal.getId());
            
        } else {
            // Anonymous user - check IP-based limits
            String clientIP = getClientIP(request);
            
            if (!usageService.canAnonymousUserSearch(clientIP)) {
                log.warn("Rate limit exceeded for anonymous user with IP: {}", clientIP);
                sendRateLimitResponse(response, "Daily search limit reached. Please register for a free account to continue searching.");
                return false;
            }
            
            // Record the usage
            usageService.recordAnonymousUsage(clientIP, request);
            log.debug("Recorded anonymous search for IP: {}", clientIP);
        }
        
        return true; // Continue with request
    }
    
    private boolean isSearchEndpoint(String requestURI) {
        return requestURI.contains("/search") && 
               (requestURI.contains("/v1/search") || requestURI.contains("/api/search"));
    }
    
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }
    
    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String jsonResponse = String.format(
            "{\"success\": false, \"message\": \"%s\", \"errorCode\": \"RATE_LIMIT_EXCEEDED\", \"timestamp\": \"%s\"}",
            message,
            java.time.Instant.now().toString()
        );
        
        response.getWriter().write(jsonResponse);
    }
}
