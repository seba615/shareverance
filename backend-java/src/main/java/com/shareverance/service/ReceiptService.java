package com.shareverance.service;

import com.shareverance.dto.ReceiptResponse;
import com.shareverance.dto.ReceiptUploadResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReceiptService {
    ReceiptUploadResponse storeReceipt(Long organizationId, Long userId, MultipartFile file);
    Resource loadReceiptFile(Long organizationId, String receiptId, Long userId);
    ReceiptResponse getReceiptMetadata(Long organizationId, String receiptId, Long userId);
    List<ReceiptResponse> getReceiptsByOrganization(Long organizationId, Long userId);
    void linkReceiptToExpense(String receiptId, Long expenseId, Long organizationId, Long userId);
}
