package com.threatscopebackend.controller.consultation;

import com.threatscopebackend.dto.consultation.request.SendMessageRequest;
import com.threatscopebackend.dto.consultation.response.ChatMessageResponse;
import com.threatscopebackend.dto.consultation.response.SessionChatResponse;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.consultation.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/consultation")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final ChatService chatService;
    
    // ===== CHAT MESSAGES =====
    
    @PostMapping("/{sessionId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody SendMessageRequest request) {
        
        log.info("📤 DEBUG: Sending message in session: {} from user: {} (ID: {})", 
                sessionId, userPrincipal.getEmail(), userPrincipal.getId());
        log.debug("Message content: {}", request.getContent());
        
        try {
            ChatMessageResponse message = chatService.sendMessage(sessionId, userPrincipal, request);
            log.info("✅ Message sent successfully in session: {}, message ID: {}", 
                    sessionId, message.getId());
            return ResponseEntity.ok(ApiResponse.success("Message sent successfully", message));
        } catch (Exception e) {
            log.error("❌ Error sending message in session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ChatMessageResponse>error("Failed to send message: " + e.getMessage()));
        }
    }
    
    @GetMapping("/{sessionId}/chat")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SessionChatResponse>> getSessionChat(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.info("🔍 DEBUG: Fetching chat for session: {} for user: {} (ID: {})", 
                sessionId, userPrincipal.getEmail(), userPrincipal.getId());
        
        try {
            SessionChatResponse chat = chatService.getSessionChat(sessionId, userPrincipal);
            log.info("✅ Successfully retrieved chat for session: {} with {} messages", 
                    sessionId, chat.getMessages().size());
            return ResponseEntity.ok(ApiResponse.success("Session chat retrieved successfully", chat));
        } catch (Exception e) {
            log.error("❌ Error fetching chat for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<SessionChatResponse>error("Failed to fetch chat: " + e.getMessage()));
        }
    }
    
    @GetMapping("/{sessionId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getSessionMessages(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        log.debug("Fetching messages for session: {} with pagination", sessionId);
        
        Page<ChatMessageResponse> messages = chatService.getSessionMessages(sessionId, userPrincipal, page, size);
        
        return ResponseEntity.ok(ApiResponse.success("Session messages retrieved successfully", messages));
    }
    
    @PostMapping("/{sessionId}/messages/read")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> markMessagesAsRead(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.debug("Marking messages as read for session: {}", sessionId);
        
        chatService.markMessagesAsRead(sessionId, userPrincipal);
        
        return ResponseEntity.ok(ApiResponse.<Void>success("Messages marked as read successfully", null));
    }
    
    @GetMapping("/{sessionId}/messages/search")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> searchMessages(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam String q) {
        
        log.debug("Searching messages in session: {} for: {}", sessionId, q);
        
        List<ChatMessageResponse> messages = chatService.searchMessages(sessionId, userPrincipal, q);
        
        return ResponseEntity.ok(ApiResponse.success("Message search completed successfully", messages));
    }
    
    @GetMapping("/{sessionId}/messages/statistics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Object[]>> getMessageStatistics(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.debug("Fetching message statistics for session: {}", sessionId);
        
        Object[] statistics = chatService.getMessageStatistics(sessionId, userPrincipal);
        
        return ResponseEntity.ok(ApiResponse.success("Message statistics retrieved successfully", statistics));
    }
    
    // ===== SYSTEM MESSAGES (Admin only) =====
    
    @PostMapping("/{sessionId}/system-message")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> createSystemMessage(
            @PathVariable Long sessionId,
            @RequestParam String content) {
        
        log.info("Creating system message for session: {}", sessionId);
        
        ChatMessageResponse message = chatService.createSystemMessage(sessionId, content);
        
        return ResponseEntity.ok(ApiResponse.success("System message created successfully", message));
    }
}
