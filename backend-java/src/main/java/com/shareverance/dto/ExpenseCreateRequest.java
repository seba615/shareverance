package com.shareverance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseCreateRequest(
    Long organizationId,
    Long createdByUserId,
    Long paidByMembershipId,
    Long categoryId,
    BigDecimal amount,
    LocalDate expenseDate,
    String description,
    String receiptUrl,
    SplitMode splitMode,
    List<Long> splitMemberIds,
    List<ExpenseSplitRequest> customSplits
) {}

