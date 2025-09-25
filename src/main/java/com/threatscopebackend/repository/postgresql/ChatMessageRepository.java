package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.ChatMessage;
import com.threatscopebackend.entity.postgresql.ConsultationSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    // Find messages by session ordered by creation time
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ConsultationSession session);
    
    // Find messages by session with pagination
    Page<ChatMessage> findBySessionOrderByCreatedAtAsc(ConsultationSession session, Pageable pageable);
    
    // Find recent messages for session
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "AND cm.createdAt >= :since ORDER BY cm.createdAt ASC")
    List<ChatMessage> findRecentMessages(@Param("session") ConsultationSession session,
                                        @Param("since") LocalDateTime since);
    
    // Count unread messages for user in session
    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.session = :session " +
           "AND cm.sender != 'USER' AND cm.isRead = false")
    long countUnreadMessagesForUser(@Param("session") ConsultationSession session);
    
    // Count unread messages for expert in session
    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.session = :session " +
           "AND cm.sender != 'EXPERT' AND cm.isRead = false")
    long countUnreadMessagesForExpert(@Param("session") ConsultationSession session);
    
    // Find unread messages for user
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "AND cm.sender != 'USER' AND cm.isRead = false ORDER BY cm.createdAt ASC")
    List<ChatMessage> findUnreadMessagesForUser(@Param("session") ConsultationSession session);
    
    // Find unread messages for expert
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "AND cm.sender != 'EXPERT' AND cm.isRead = false ORDER BY cm.createdAt ASC")
    List<ChatMessage> findUnreadMessagesForExpert(@Param("session") ConsultationSession session);
    
    // Mark messages as read for user
    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true WHERE cm.session = :session " +
           "AND cm.sender != 'USER' AND cm.isRead = false")
    int markMessagesAsReadForUser(@Param("session") ConsultationSession session);
    
    // Mark messages as read for expert
    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.isRead = true WHERE cm.session = :session " +
           "AND cm.sender != 'EXPERT' AND cm.isRead = false")
    int markMessagesAsReadForExpert(@Param("session") ConsultationSession session);
    
    // Find system messages for session
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "AND cm.isSystemMessage = true ORDER BY cm.createdAt ASC")
    List<ChatMessage> findSystemMessages(@Param("session") ConsultationSession session);
    
    // Find file messages for session
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "AND cm.type = 'FILE' ORDER BY cm.createdAt ASC")
    List<ChatMessage> findFileMessages(@Param("session") ConsultationSession session);
    
    // Find messages by type
    List<ChatMessage> findBySessionAndType(ConsultationSession session, ChatMessage.MessageType type);
    
    // Find messages by sender
    List<ChatMessage> findBySessionAndSenderOrderByCreatedAtAsc(ConsultationSession session, 
                                                               ChatMessage.MessageSender sender);
    
    // Search messages in session
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "AND LOWER(cm.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY cm.createdAt ASC")
    List<ChatMessage> searchMessagesInSession(@Param("session") ConsultationSession session,
                                             @Param("searchTerm") String searchTerm);
    
    // Get message count for session
    long countBySession(ConsultationSession session);
    
    // Get message statistics for session
    @Query("SELECT COUNT(cm), " +
           "COUNT(CASE WHEN cm.sender = 'USER' THEN 1 END), " +
           "COUNT(CASE WHEN cm.sender = 'EXPERT' THEN 1 END), " +
           "COUNT(CASE WHEN cm.sender = 'SYSTEM' THEN 1 END) " +
           "FROM ChatMessage cm WHERE cm.session = :session")
    Object[] getMessageStatistics(@Param("session") ConsultationSession session);
    
    // Find last message in session
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "ORDER BY cm.createdAt DESC LIMIT 1")
    ChatMessage findLastMessage(@Param("session") ConsultationSession session);
    
    // Find first message in session
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "ORDER BY cm.createdAt ASC LIMIT 1")
    ChatMessage findFirstMessage(@Param("session") ConsultationSession session);
    
    // Delete old messages (cleanup)
    @Modifying
    @Query("DELETE FROM ChatMessage cm WHERE cm.session.id IN " +
           "(SELECT cs.id FROM ConsultationSession cs WHERE cs.completedAt < :cutoffDate)")
    int deleteOldMessages(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Find messages created after timestamp (for real-time updates)
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.session = :session " +
           "AND cm.createdAt > :lastMessageTime ORDER BY cm.createdAt ASC")
    List<ChatMessage> findNewMessages(@Param("session") ConsultationSession session,
                                     @Param("lastMessageTime") LocalDateTime lastMessageTime);
}
