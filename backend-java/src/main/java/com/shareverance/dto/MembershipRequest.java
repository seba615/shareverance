package com.shareverance.dto;

import com.shareverance.model.MembershipRole;

public record MembershipRequest(
    Long userId,
    Long organizationId,
    MembershipRole role
) {}

