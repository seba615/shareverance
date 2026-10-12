package com.shareverance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ServiceTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    String scope
) {}

