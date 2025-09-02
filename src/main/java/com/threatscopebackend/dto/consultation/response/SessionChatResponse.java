package com.threatscopebackend.dto.consultation.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionChatResponse {
    private ConsultationSessionResponse session;
    private List<ChatMessageResponse> messages;
    private List<ConsultationFileResponse> files;
    private Long unreadCount;
    private Boolean canSendMessages;
}
