package com.threatscopebackend.dto.consultation.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationFileResponse {
    private Long id;
    private String filename;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String formattedFileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;
    private Integer downloadCount;
    private Boolean isImage;
    private Boolean isPdf;
    private Boolean isDocument;
    private Boolean isExpired;
    
    public static ConsultationFileResponse fromEntity(com.threatscopebackend.entity.postgresql.ConsultationFile file) {
        return ConsultationFileResponse.builder()
            .id(file.getId())
            .filename(file.getFilename())
            .originalFilename(file.getOriginalFilename())
            .contentType(file.getContentType())
            .fileSize(file.getFileSize())
            .formattedFileSize(file.getFormattedFileSize())
            .uploadedBy(file.getUploadedBy().toString())
            .uploadedAt(file.getUploadedAt())
            .expiresAt(file.getExpiresAt())
            .downloadCount(file.getDownloadCount())
            .isImage(file.isImage())
            .isPdf(file.isPdf())
            .isDocument(file.isDocument())
            .isExpired(file.isExpired())
            .build();
    }
}
