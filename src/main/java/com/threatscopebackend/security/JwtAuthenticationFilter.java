package com.threatscopebackend.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        log.debug("🔍 JWT Filter processing request: {} {}", request.getMethod(), requestURI);
        
        try {
            String jwt = getJwtFromRequest(request);
            log.debug("🔑 JWT Token extracted: {}", jwt != null ? "Present (" + jwt.length() + " chars)" : "Missing");
            
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                log.debug("✅ JWT Token valid for user ID: {}", userId);
                
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                
                if (userDetails != null && userDetails.isEnabled()) {
                    log.debug("✅ User details loaded: {} (enabled: {})", userDetails.getUsername(), userDetails.isEnabled());
                    
                    UsernamePasswordAuthenticationToken authentication = 
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("✅ Authentication set in SecurityContext for user: {}", userDetails.getUsername());
                } else {
                    log.warn("❌ User details null or disabled for ID: {}", userId);
                }
            } else {
                log.debug("❌ JWT Token invalid or missing for request: {}", requestURI);
            }
        } catch (Exception ex) {
            log.error("💥 Could not set user authentication in security context for request: " + requestURI, ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        return null;
    }
}
