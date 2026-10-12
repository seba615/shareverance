package com.shareverance.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryTotalDto(
    @JsonProperty("category_id") Long categoryId,
    @JsonProperty("amount_minor") Long amountMinor
) {}

