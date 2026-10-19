package com.shareverance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseCalendarDayResponse(
        LocalDate date,
        BigDecimal totalAmount,
        long expenseCount
) {}
