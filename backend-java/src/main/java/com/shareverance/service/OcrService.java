package com.shareverance.service;

import com.shareverance.dto.OcrResultResponse;

public interface OcrService {
    OcrResultResponse processReceiptOcr(Long organizationId, String receiptId, Long userId);
}
