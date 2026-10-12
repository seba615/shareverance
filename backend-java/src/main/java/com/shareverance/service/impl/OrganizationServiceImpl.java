package com.shareverance.service.impl;

import com.shareverance.dto.OrganizationRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.exception.ResourceNotFoundException;
import com.shareverance.model.Organization;
import com.shareverance.repository.OrganizationRepository;
import com.shareverance.service.OrganizationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationServiceImpl(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Override
    public Organization createOrganization(OrganizationRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessRuleException("El nombre de la organización no puede estar vacío");
        }
        if (request.type() == null) {
            throw new BusinessRuleException("El tipo de organización es obligatorio");
        }

        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency().toUpperCase()
                : "ARS";

        Organization organization = new Organization(
                null,
                request.name(),
                request.type(),
                currency,
                LocalDateTime.now(),
                true
        );

        return organizationRepository.save(organization);
    }

    @Override
    public Organization getOrganizationById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organización no encontrada con id: " + id));
    }

    @Override
    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    @Override
    public Organization updateOrganization(Long id, OrganizationRequest request) {
        Organization organization = getOrganizationById(id);

        if (request.name() != null && !request.name().isBlank()) {
            organization.setName(request.name());
        }
        if (request.type() != null) {
            organization.setType(request.type());
        }
        if (request.currency() != null && !request.currency().isBlank()) {
            organization.setCurrency(request.currency().toUpperCase());
        }

        return organizationRepository.save(organization);
    }

    @Override
    public Organization deactivateOrganization(Long id) {
        Organization organization = getOrganizationById(id);
        organization.setIsActive(false);
        return organizationRepository.save(organization);
    }
}

