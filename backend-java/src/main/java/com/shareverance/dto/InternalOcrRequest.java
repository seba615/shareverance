package com.shareverance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InternalOcrRequest(
    @JsonProperty("contract_version") String contractVersion,
    @JsonProperty("request_id") String requestId,
    @JsonProperty("organization_id") Long organizationId,
    @JsonProperty("receipt_id") String receiptId,
    @JsonProperty("storage_key") String storageKey
) {}
