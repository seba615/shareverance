package com.shareverance.service;

import com.shareverance.dto.MembershipRequest;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.UserOrganization;

import java.util.List;

public interface MembershipService {
    UserOrganization addMemberToOrganization(MembershipRequest request);
    UserOrganization getMembershipById(Long id);
    List<UserOrganization> getMembershipsByOrganization(Long organizationId);
    List<UserOrganization> getMembershipsByUser(Long userId);
    UserOrganization updateMemberRole(Long id, MembershipRole newRole);
    UserOrganization deactivateMembership(Long id);
}

