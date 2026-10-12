package com.shareverance.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryCoverageDto(
    @JsonProperty("category_id") Long categoryId,
    @JsonProperty("available_from_month") String availableFromMonth
) {}

