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
@Table(name = "consultation_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationFile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private ConsultationSession session;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_message_id")
    @JsonIgnore
    private ChatMessage chatMessage;
    
    @Column(name = "filename", nullable = false)
    private String filename;
    
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;
    
    @Column(name = "content_type")
    private String contentType;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "storage_path", nullable = false)
    private String storagePath;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "uploaded_by", nullable = false)
    private FileUploader uploadedBy;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @CreationTimestamp
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;
    
    @Column(name = "download_count")
    private Integer downloadCount = 0;
    
    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;
    
    // Enums
    public enum FileUploader {
        USER,
        EXPERT,
        SYSTEM
    }
    
    // Helper methods
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public void recordDownload() {
        this.downloadCount = (this.downloadCount != null ? this.downloadCount : 0) + 1;
        this.lastDownloadedAt = LocalDateTime.now();
    }
    
    public String getFormattedFileSize() {
        if (fileSize == null) return "Unknown size";
        
        long bytes = fileSize;
        if (bytes < 1024) return bytes + " B";
        
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
    
    public boolean isPdf() {
        return contentType != null && contentType.equals("application/pdf");
    }
    
    public boolean isDocument() {
        return contentType != null && (
            contentType.startsWith("application/vnd.openxmlformats") ||
            contentType.equals("application/msword") ||
            contentType.equals("text/plain")
        );
    }
    
    @PrePersist
    protected void onCreate() {
        if (expiresAt == null) {
            // Files expire 24 hours after session completion, or 48 hours after upload
            expiresAt = LocalDateTime.now().plusHours(48);
        }
        if (downloadCount == null) {
            downloadCount = 0;
        }
    }
}
