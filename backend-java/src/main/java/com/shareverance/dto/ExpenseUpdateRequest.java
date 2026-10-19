package com.shareverance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseUpdateRequest(
    Long categoryId,
    BigDecimal amount,
    LocalDate expenseDate,
    String description,
    String receiptUrl,
    Integer version
) {
    public ExpenseUpdateRequest(Long categoryId, BigDecimal amount, LocalDate expenseDate,
                                String description, String receiptUrl) {
        this(categoryId, amount, expenseDate, description, receiptUrl, null);
    }
}

