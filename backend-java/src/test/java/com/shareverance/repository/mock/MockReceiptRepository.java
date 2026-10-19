package com.shareverance.repository.mock;

import com.shareverance.model.Receipt;
import com.shareverance.repository.ReceiptRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MockReceiptRepository implements ReceiptRepository {

    private final Map<String, Receipt> store = new ConcurrentHashMap<>();

    @Override
    public Receipt save(Receipt receipt) {
        if (receipt.getId() == null || receipt.getId().isBlank()) {
            receipt.setId(UUID.randomUUID().toString());
        }
        if (receipt.getCreatedAt() == null) {
            receipt.setCreatedAt(Instant.now());
        }
        if (receipt.getIsActive() == null) {
            receipt.setIsActive(true);
        }
        store.put(receipt.getId(), receipt);
        return receipt;
    }

    @Override
    public Optional<Receipt> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Receipt> findByIdAndOrganizationId(String id, Long organizationId) {
        Receipt receipt = store.get(id);
        if (receipt != null && receipt.getOrganizationId().equals(organizationId)) {
            return Optional.of(receipt);
        }
        return Optional.empty();
    }

    @Override
    public List<Receipt> findByOrganizationId(Long organizationId) {
        List<Receipt> list = new ArrayList<>();
        for (Receipt receipt : store.values()) {
            if (receipt.getOrganizationId().equals(organizationId)) {
                list.add(receipt);
            }
        }
        return list;
    }

    @Override
    public Optional<Receipt> findByExpenseId(Long expenseId) {
        for (Receipt receipt : store.values()) {
            if (expenseId.equals(receipt.getExpenseId())) {
                return Optional.of(receipt);
            }
        }
        return Optional.empty();
    }

    @Override
    public void updateExpenseId(String receiptId, Long expenseId) {
        Receipt receipt = store.get(receiptId);
        if (receipt != null) {
            receipt.setExpenseId(expenseId);
        }
    }

    @Override
    public void deactivateById(String id) {
        Receipt receipt = store.get(id);
        if (receipt != null) {
            receipt.setIsActive(false);
        }
    }
}
