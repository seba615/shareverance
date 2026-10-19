package com.shareverance.dto;

import com.shareverance.model.MembershipRole;

public record MembershipRequest(
    Long userId,
    Long organizationId,
    MembershipRole role,
    String email
) {
    public MembershipRequest(Long userId, Long organizationId, MembershipRole role) {
        this(userId, organizationId, role, null);
    }
}

