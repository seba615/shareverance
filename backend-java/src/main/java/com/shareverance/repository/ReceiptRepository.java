package com.shareverance.repository;

import com.shareverance.model.Receipt;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository {
    Receipt save(Receipt receipt);
    Optional<Receipt> findById(String id);
    Optional<Receipt> findByIdAndOrganizationId(String id, Long organizationId);
    List<Receipt> findByOrganizationId(Long organizationId);
    Optional<Receipt> findByExpenseId(Long expenseId);
    void updateExpenseId(String receiptId, Long expenseId);
    void deactivateById(String id);
}
