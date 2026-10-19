package com.shareverance.repository;

import com.shareverance.model.UserOrganization;

import java.util.List;
import java.util.Optional;

public interface UserOrganizationRepository {
    UserOrganization save(UserOrganization userOrganization);
    Optional<UserOrganization> findById(Long id);
    Optional<UserOrganization> findByIdForUpdate(Long id);
    Optional<UserOrganization> findByUserIdAndOrganizationId(Long userId, Long organizationId);
    List<UserOrganization> findByOrganizationId(Long organizationId);
    List<UserOrganization> findByUserId(Long userId);
    long countActiveAdminsByOrganizationId(Long organizationId);
    boolean existsById(Long id);
    boolean existsByUserIdAndOrganizationId(Long userId, Long organizationId);
}

