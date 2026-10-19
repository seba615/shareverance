package com.shareverance.dto;
import java.math.BigDecimal;
import java.util.List;
public record ExpensePageResponse(List<ExpenseDetailResponse> items, int page, int size,
        long totalElements, long totalPages, BigDecimal totalAmount) {}
