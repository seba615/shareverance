package com.shareverance.dto;

import java.time.Instant;

public record ReceiptUploadResponse(
    String receiptId,
    Long organizationId,
    String originalFilename,
    String contentType,
    long fileSizeBytes,
    Instant createdAt,
    String status
) {}
