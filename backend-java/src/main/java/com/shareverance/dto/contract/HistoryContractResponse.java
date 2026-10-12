package com.shareverance.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryContractResponse(
    @JsonProperty("contract_version") String contractVersion,
    @JsonProperty("request_id") String requestId,
    @JsonProperty("organization_id") Long organizationId,
    @JsonProperty("currency") String currency,
    @JsonProperty("target_month") String targetMonth,
    @JsonProperty("categories") List<CategoryCoverageDto> categories,
    @JsonProperty("history") List<MonthlyHistoryDto> history
) {}

