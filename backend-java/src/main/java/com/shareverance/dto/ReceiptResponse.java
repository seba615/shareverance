package com.shareverance.dto;

import java.time.Instant;

public record ReceiptResponse(
    String receiptId,
    Long organizationId,
    Long uploadedByUserId,
    String originalFilename,
    String contentType,
    long fileSizeBytes,
    Long expenseId,
    Instant createdAt
) {}
