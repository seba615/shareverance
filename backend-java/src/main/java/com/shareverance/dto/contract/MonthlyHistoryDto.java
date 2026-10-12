package com.shareverance.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MonthlyHistoryDto(
    @JsonProperty("month") String month,
    @JsonProperty("category_totals") List<CategoryTotalDto> categoryTotals
) {}

