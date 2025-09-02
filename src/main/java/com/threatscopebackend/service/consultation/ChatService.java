package com.threatscopebackend.service.consultation;

import com.threatscopebackend.dto.consultation.request.SendMessageRequest;
import com.threatscopebackend.dto.consultation.response.ChatMessageResponse;
import com.threatscopebackend.dto.consultation.response.SessionChatResponse;
import com.threatscopebackend.entity.postgresql.ChatMessage;
import com.threatscopebackend.entity.postgresql.ConsultationSession;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.ChatMessageRepository;
import com.threatscopebackend.repository.postgresql.ConsultationSessionRepository;
import com.threatscopebackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    
    private final ChatMessageRepository chatMessageRepository;
    private final ConsultationSessionRepository consultationSessionRepository;
    
    /**
     * Send a message in a consultation session
     */
    @Transactional
    public ChatMessageResponse sendMessage(
            Long sessionId, 
            UserPrincipal userPrincipal, 
            SendMessageRequest request) {
        
        log.debug("Sending message in session: {} from user: {}", sessionId, userPrincipal.getEmail());
        
        // Validate session and user access
        ConsultationSession session = validateSessionAccess(sessionId, userPrincipal);
        
        // Determine sender type and get user info
        ChatMessage.MessageSender sender = ChatMessage.MessageSender.USER;
        String senderName = getSenderName(userPrincipal);
        if (isExpert(userPrincipal, session)) {
            sender = ChatMessage.MessageSender.EXPERT;
            senderName = "Security Expert"; // Or get from expert profile
        }
        
        // Determine message type
        ChatMessage.MessageType messageType = parseMessageType(request.getMessageType(), request.getContent());
        
        // Create message with sender information
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sender(sender)
                .type(messageType)
                .content(request.getContent())
                .isRead(false)
                .isSystemMessage(false)
                .senderUserId(userPrincipal.getId()) // NEW: Set actual user ID
                .senderName(senderName) // NEW: Set display name
                .build();
        
        // Save message to database (this stays as REST API)
        message = chatMessageRepository.save(message);
        
        // REMOVED: No automatic WebSocket notifications for chat messages
        // Chat messages are sent via WebSocket directly, not via notifications
        
        log.info("Message sent and saved to database: sessionId={}, messageId={}", 
                sessionId, message.getId());
        
        return ChatMessageResponse.fromEntity(message);
    }
    
    /**
     * Get chat messages for a session
     */
    @Transactional(readOnly = true)
    public SessionChatResponse getSessionChat(Long sessionId, UserPrincipal userPrincipal) {
        log.debug("Fetching chat for session: {} for user: {}", sessionId, userPrincipal.getEmail());
        
        // Validate session and user access
        ConsultationSession session = validateSessionAccess(sessionId, userPrincipal);
        
        // Get messages
        List<ChatMessage> messages = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
        
        // Get files (will be implemented in file service)
        // List<ConsultationFileResponse> files = fileService.getSessionFiles(sessionId);
        
        // Calculate unread count for user
        long unreadCount = isExpert(userPrincipal, session) ?
                chatMessageRepository.countUnreadMessagesForExpert(session) :
                chatMessageRepository.countUnreadMessagesForUser(session);
        
        // Check if user can send messages
        boolean canSendMessages = (session.getStatus() == ConsultationSession.SessionStatus.ACTIVE ||
                                 session.getStatus() == ConsultationSession.SessionStatus.ASSIGNED) &&
                                 session.getStatus() != ConsultationSession.SessionStatus.COMPLETED;
        
        return SessionChatResponse.builder()
                .session(com.threatscopebackend.dto.consultation.response.ConsultationSessionResponse.fromEntity(session))
                .messages(messages.stream().map(ChatMessageResponse::fromEntity).toList())
                .files(List.of()) // TODO: Implement file listing
                .unreadCount(unreadCount)
                .canSendMessages(canSendMessages)
                .build();
    }
    
    /**
     * Get messages with pagination
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getSessionMessages(
            Long sessionId, 
            UserPrincipal userPrincipal, 
            int page, 
            int size) {
        
        // Validate session and user access
        ConsultationSession session = validateSessionAccess(sessionId, userPrincipal);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<ChatMessage> messages = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session, pageable);
        
        return messages.map(ChatMessageResponse::fromEntity);
    }
    
    /**
     * Mark messages as read
     */
    @Transactional
    public void markMessagesAsRead(Long sessionId, UserPrincipal userPrincipal) {
        log.debug("Marking messages as read for session: {} by user: {}", sessionId, userPrincipal.getEmail());
        
        // Validate session and user access
        ConsultationSession session = validateSessionAccess(sessionId, userPrincipal);
        
        // Mark appropriate messages as read based on user type
        int markedCount;
        if (isExpert(userPrincipal, session)) {
            markedCount = chatMessageRepository.markMessagesAsReadForExpert(session);
        } else {
            markedCount = chatMessageRepository.markMessagesAsReadForUser(session);
        }
        
        log.debug("Marked {} messages as read for session: {}", markedCount, sessionId);
    }
    
    /**
     * Get recent messages (for real-time updates)
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getRecentMessages(
            Long sessionId, 
            UserPrincipal userPrincipal, 
            LocalDateTime since) {
        
        // Validate session and user access
        ConsultationSession session = validateSessionAccess(sessionId, userPrincipal);
        
        List<ChatMessage> messages = chatMessageRepository.findRecentMessages(session, since);
        
        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }
    
    /**
     * Search messages in a session
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> searchMessages(
            Long sessionId, 
            UserPrincipal userPrincipal, 
            String searchTerm) {
        
        // Validate session and user access
        ConsultationSession session = validateSessionAccess(sessionId, userPrincipal);
        
        List<ChatMessage> messages = chatMessageRepository.searchMessagesInSession(session, searchTerm);
        
        return messages.stream()
                .map(ChatMessageResponse::fromEntity)
                .toList();
    }
    
    /**
     * Get message statistics for a session
     */
    @Transactional(readOnly = true)
    public Object[] getMessageStatistics(Long sessionId, UserPrincipal userPrincipal) {
        // Validate session and user access
        ConsultationSession session = validateSessionAccess(sessionId, userPrincipal);
        
        return chatMessageRepository.getMessageStatistics(session);
    }
    
    /**
     * Save a system message (internal use)
     */
    @Transactional
    public ChatMessage saveMessage(ChatMessage message) {
        ChatMessage saved = chatMessageRepository.save(message);
        
        // REMOVED: No WebSocket notifications for system messages
        // System messages are rare and can be handled via manual refresh
        
        return saved;
    }
    
    /**
     * Create and save a system message
     */
    @Transactional
    public ChatMessageResponse createSystemMessage(Long sessionId, String content) {
        ConsultationSession session = consultationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation session not found"));
        
        ChatMessage message = ChatMessage.createSystemMessage(session, content);
        message = saveMessage(message);
        
        return ChatMessageResponse.fromEntity(message);
    }
    
    // Helper methods
    
    private String getSenderName(UserPrincipal userPrincipal) {
        // Option 1: Use the name field from UserPrincipal (firstName + lastName)
        if (userPrincipal.getName() != null && !userPrincipal.getName().trim().isEmpty()) {
            return userPrincipal.getName();
        }
        
        // Option 2: Get from the User entity if available
        if (userPrincipal.getUser() != null) {
            User user = userPrincipal.getUser();
            String firstName = user.getFirstName() != null ? user.getFirstName() : "";
            String lastName = user.getLastName() != null ? user.getLastName() : "";
            String fullName = (firstName + " " + lastName).trim();
            if (!fullName.isEmpty()) {
                return fullName;
            }
        }
        
        // Option 3: Fallback to email or "User"
        if (userPrincipal.getEmail() != null) {
            return userPrincipal.getEmail().split("@")[0]; // Use email prefix
        }
        
        return "User"; // Final fallback
    }
    
    private ConsultationSession validateSessionAccess(Long sessionId, UserPrincipal userPrincipal) {
        ConsultationSession session = consultationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation session not found"));
        
        // Check if user has access to this session
        boolean isOwner = session.getUser().getId().equals(userPrincipal.getId());
        
        // Check if user is admin (ADD ADMIN CHECK)
        boolean isAdmin = userPrincipal.getAuthorities().stream()
                .anyMatch(authority -> 
                    authority.getAuthority().equals("ROLE_ADMIN") || 
                    authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
        
        // Check if user is assigned expert (SAFE: compare IDs, don't load Expert entity)
        boolean isAssignedExpert = false;
        if (session.getExpert() != null) {
            // Get expert ID without triggering full entity load
            Long expertId = session.getExpert().getId();
            isAssignedExpert = expertId != null && expertId.equals(userPrincipal.getId());
        }
        
        boolean hasAccess = isOwner || isAdmin || isAssignedExpert;
        
        if (!hasAccess) {
            throw new IllegalArgumentException("User does not have access to this session");
        }
        
        return session;
    }
    
    private boolean isExpert(UserPrincipal userPrincipal, ConsultationSession session) {
        // SAFE: Compare IDs instead of loading email from Expert entity
        return session.getExpert() != null && 
               session.getExpert().getId() != null &&
               session.getExpert().getId().equals(userPrincipal.getId());
    }
    
    private ChatMessage.MessageType parseMessageType(String messageType, String content) {
        if (messageType == null) {
            return ChatMessage.MessageType.TEXT;
        }
        
        try {
            ChatMessage.MessageType type = ChatMessage.MessageType.valueOf(messageType.toUpperCase());
            
            // Auto-detect Zoom links
            if (type == ChatMessage.MessageType.LINK && 
                content != null && content.toLowerCase().contains("zoom.us")) {
                return ChatMessage.MessageType.ZOOM_LINK;
            }
            
            return type;
        } catch (IllegalArgumentException e) {
            return ChatMessage.MessageType.TEXT;
        }
    }
    
    /**
     * Cleanup old messages (scheduled task)
     */
    @Transactional
    public void cleanupOldMessages() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90); // Keep messages for 90 days
        int deletedCount = chatMessageRepository.deleteOldMessages(cutoffDate);
        
        if (deletedCount > 0) {
            log.info("Cleaned up {} old chat messages", deletedCount);
        }
    }
}
