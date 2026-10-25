package com.shareverance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IndividualSummaryResponse(Long organizationId, Long membershipId, String currency,
        LocalDate fromDate, LocalDate toDate, BigDecimal totalAmount, long expenseCount,
        List<CategoryParticipation> categories) {
    public record CategoryParticipation(Long categoryId, String categoryName, boolean categoryActive,
            BigDecimal amount, long expenseCount) {}
}
