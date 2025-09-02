package com.threatscopebackend.entity.postgresql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private ConsultationSession session;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sender", nullable = false)
    private MessageSender sender;
    
    // NEW: Track the actual user who sent this message
    @Column(name = "sender_user_id")
    private Long senderUserId;
    
    // NEW: Store sender name for display
    @Column(name = "sender_name")
    private String senderName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType type = MessageType.TEXT;
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "edited_at")
    private LocalDateTime editedAt;
    
    @Column(name = "is_system_message", nullable = false)
    private Boolean isSystemMessage = false;
    
    // Enums
    public enum MessageSender {
        USER,
        EXPERT,
        SYSTEM
    }
    
    public enum MessageType {
        TEXT,
        FILE,
        LINK,
        ZOOM_LINK,
        SYSTEM_MESSAGE,
        SESSION_START,
        SESSION_END
    }
    
    // Helper methods
    public boolean isFromUser() {
        return sender == MessageSender.USER;
    }
    
    public boolean isFromExpert() {
        return sender == MessageSender.EXPERT;
    }
    
    public boolean isFromSystem() {
        return sender == MessageSender.SYSTEM || isSystemMessage;
    }
    
    public boolean isFileMessage() {
        return type == MessageType.FILE;
    }
    
    public boolean isZoomLink() {
        return type == MessageType.ZOOM_LINK || 
               (type == MessageType.LINK && content != null && 
                content.toLowerCase().contains("zoom.us"));
    }
    
    public void markAsRead() {
        this.isRead = true;
    }
    
    // Factory methods for common message types
    public static ChatMessage createSystemMessage(ConsultationSession session, String content) {
        return ChatMessage.builder()
            .session(session)
            .sender(MessageSender.SYSTEM)
            .type(MessageType.SYSTEM_MESSAGE)
            .content(content)
            .isSystemMessage(true)
            .isRead(false)
            .senderUserId(null) // System messages have no user ID
            .senderName("System")
            .build();
    }
    
    public static ChatMessage createSessionStartMessage(ConsultationSession session) {
        return createSystemMessage(session, 
            "Expert " + session.getExpert().getName() + " has joined the session");
    }
    
    public static ChatMessage createSessionEndMessage(ConsultationSession session) {
        return createSystemMessage(session, 
            "Session completed. Thank you for using ThreatScope consultation!");
    }
    
    public static ChatMessage createUserMessage(ConsultationSession session, String content, Long userId, String userName) {
        return ChatMessage.builder()
            .session(session)
            .sender(MessageSender.USER)
            .type(MessageType.TEXT)
            .content(content)
            .isRead(false)
            .senderUserId(userId)
            .senderName(userName)
            .build();
    }
    
    public static ChatMessage createExpertMessage(ConsultationSession session, String content, Long expertId, String expertName) {
        return ChatMessage.builder()
            .session(session)
            .sender(MessageSender.EXPERT)
            .type(MessageType.TEXT)
            .content(content)
            .isRead(false)
            .senderUserId(expertId)
            .senderName(expertName)
            .build();
    }
    
    public static ChatMessage createFileMessage(ConsultationSession session, MessageSender sender, String filename) {
        return ChatMessage.builder()
            .session(session)
            .sender(sender)
            .type(MessageType.FILE)
            .content("Shared file: " + filename)
            .isRead(false)
            .build();
    }
    
    public static ChatMessage createLinkMessage(ConsultationSession session, MessageSender sender, String url) {
        MessageType type = url.toLowerCase().contains("zoom.us") ? 
            MessageType.ZOOM_LINK : MessageType.LINK;
            
        return ChatMessage.builder()
            .session(session)
            .sender(sender)
            .type(type)
            .content(url)
            .isRead(false)
            .build();
    }
}
