package com.shareverance.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseCalendarResponse(
        String month,
        BigDecimal totalAmount,
        long totalExpenses,
        List<ExpenseCalendarDayResponse> days
) {}
