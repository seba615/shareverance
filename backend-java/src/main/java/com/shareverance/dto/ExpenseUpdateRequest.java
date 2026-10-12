package com.shareverance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseUpdateRequest(
    Long categoryId,
    BigDecimal amount,
    LocalDate expenseDate,
    String description,
    String receiptUrl
) {}

