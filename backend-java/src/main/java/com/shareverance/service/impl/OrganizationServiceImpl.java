package com.shareverance.service.impl;

import com.shareverance.dto.OrganizationRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.exception.ResourceNotFoundException;
import com.shareverance.model.Organization;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.OrganizationRepository;
import com.shareverance.repository.UserOrganizationRepository;
import com.shareverance.service.OrganizationService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository membershipRepository;

    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                                   UserOrganizationRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional
    public Organization createOrganization(OrganizationRequest request, Long creatorUserId) {
        if (creatorUserId == null) {
            throw new BusinessRuleException("Se requiere una sesión autenticada");
        }
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

        Organization saved = organizationRepository.save(organization);
        membershipRepository.save(new UserOrganization(
                null, creatorUserId, saved.getId(), MembershipRole.ADMIN, LocalDateTime.now(), true));
        return saved;
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
    public List<Organization> getOrganizationsForUser(Long userId) {
        return membershipRepository.findByUserId(userId).stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getIsActive()))
                .flatMap(membership -> organizationRepository.findById(membership.getOrganizationId()).stream())
                .filter(organization -> Boolean.TRUE.equals(organization.getIsActive()))
                .toList();
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
