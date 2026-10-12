package com.shareverance.service;

import com.shareverance.dto.OrganizationRequest;
import com.shareverance.model.Organization;

import java.util.List;

public interface OrganizationService {
    Organization createOrganization(OrganizationRequest request);
    Organization getOrganizationById(Long id);
    List<Organization> getAllOrganizations();
    Organization updateOrganization(Long id, OrganizationRequest request);
    Organization deactivateOrganization(Long id);
}

