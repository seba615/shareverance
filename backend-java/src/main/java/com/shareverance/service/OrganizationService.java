package com.shareverance.service;

import com.shareverance.dto.OrganizationRequest;
import com.shareverance.model.Organization;

import java.util.List;

public interface OrganizationService {
    Organization createOrganization(OrganizationRequest request, Long creatorUserId);
    Organization getOrganizationById(Long id);
    List<Organization> getAllOrganizations();
    List<Organization> getOrganizationsForUser(Long userId);
    Organization updateOrganization(Long id, OrganizationRequest request);
    Organization deactivateOrganization(Long id);
}
