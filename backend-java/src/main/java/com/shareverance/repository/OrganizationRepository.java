package com.shareverance.repository;

import com.shareverance.model.Organization;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {
    Organization save(Organization organization);
    Optional<Organization> findById(Long id);
    List<Organization> findAll();
    boolean existsById(Long id);
}

