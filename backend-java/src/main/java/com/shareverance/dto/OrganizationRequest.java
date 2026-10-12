package com.shareverance.dto;

import com.shareverance.model.OrganizationType;

public record OrganizationRequest(
    String name,
    OrganizationType type,
    String currency
) {}

