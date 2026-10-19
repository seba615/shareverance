package com.shareverance.model;

import java.time.Instant;

public class Receipt {
    private String id;
    private Long organizationId;
    private Long uploadedByUserId;
    private String storagePath;
    private String originalFilename;
    private String contentType;
    private Long fileSizeBytes;
    private Long expenseId;
    private Instant createdAt;
    private Boolean isActive;

    public Receipt() {
    }

    public Receipt(String id, Long organizationId, Long uploadedByUserId, String storagePath,
                   String originalFilename, String contentType, Long fileSizeBytes,
                   Long expenseId, Instant createdAt, Boolean isActive) {
        this.id = id;
        this.organizationId = organizationId;
        this.uploadedByUserId = uploadedByUserId;
        this.storagePath = storagePath;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.expenseId = expenseId;
        this.createdAt = createdAt;
        this.isActive = isActive;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }

    public void setUploadedByUserId(Long uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(Long expenseId) {
        this.expenseId = expenseId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
