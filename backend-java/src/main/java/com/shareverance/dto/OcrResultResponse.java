package com.shareverance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record OcrResultResponse(
    @JsonProperty("receiptId") String receiptId,
    @JsonProperty("status") String status,
    @JsonProperty("currency") String currency,
    @JsonProperty("date") String date,
    @JsonProperty("amount") BigDecimal amount,
    @JsonProperty("description") String description,
    @JsonProperty("warnings") List<String> warnings
) {}
