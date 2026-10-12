package com.shareverance.dto;

public record ClientCredentialsRequest(
    String clientId,
    String clientSecret
) {}

