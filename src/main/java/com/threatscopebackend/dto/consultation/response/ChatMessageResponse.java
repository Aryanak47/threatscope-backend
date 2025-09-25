package com.threatscopebackend.dto.consultation.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponse {
    private Long id;
    private String sender;
    private String messageType;
    private String content;
    private Boolean isRead;
    private Boolean isSystemMessage;
    private LocalDateTime createdAt;
    private LocalDateTime editedAt;
    private List<ConsultationFileResponse> attachments;
    
    // NEW: Added sender identification fields
    private Long senderUserId;
    private String senderName;
    
    public static ChatMessageResponse fromEntity(com.threatscopebackend.entity.postgresql.ChatMessage message) {
        return ChatMessageResponse.builder()
            .id(message.getId())
            .sender(message.getSender().toString())
            .messageType(message.getType().toString())
            .content(message.getContent())
            .isRead(message.getIsRead())
            .isSystemMessage(message.getIsSystemMessage())
            .createdAt(message.getCreatedAt())
            .editedAt(message.getEditedAt())
            .senderUserId(message.getSenderUserId())
            .senderName(message.getSenderName())
            .build();
    }
}
