package com.shareverance.dto;

import java.math.BigDecimal;

public record ExpenseSplitRequest(
    Long userOrganizationId,
    BigDecimal amount
) {}

