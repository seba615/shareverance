package com.shareverance.service.impl;

import com.shareverance.dto.MembershipRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.exception.ResourceNotFoundException;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.OrganizationRepository;
import com.shareverance.repository.UserOrganizationRepository;
import com.shareverance.repository.UserRepository;
import com.shareverance.service.MembershipService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MembershipServiceImpl implements MembershipService {

    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    public MembershipServiceImpl(UserOrganizationRepository userOrganizationRepository,
                                 UserRepository userRepository,
                                 OrganizationRepository organizationRepository) {
        this.userOrganizationRepository = userOrganizationRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public UserOrganization addMemberToOrganization(MembershipRequest request) {
        if (request.userId() == null || !userRepository.existsById(request.userId())) {
            throw new ResourceNotFoundException("Usuario no encontrado con id: " + request.userId());
        }
        if (request.organizationId() == null || !organizationRepository.existsById(request.organizationId())) {
            throw new ResourceNotFoundException("Organización no encontrada con id: " + request.organizationId());
        }
        if (userOrganizationRepository.existsByUserIdAndOrganizationId(request.userId(), request.organizationId())) {
            throw new BusinessRuleException("El usuario ya pertenece a esta organización");
        }

        MembershipRole role = request.role() != null ? request.role() : MembershipRole.USER;

        UserOrganization uo = new UserOrganization(
                null,
                request.userId(),
                request.organizationId(),
                role,
                LocalDateTime.now(),
                true
        );

        return userOrganizationRepository.save(uo);
    }

    @Override
    public UserOrganization getMembershipById(Long id) {
        return userOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada con id: " + id));
    }

    @Override
    public List<UserOrganization> getMembershipsByOrganization(Long organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organización no encontrada con id: " + organizationId);
        }
        return userOrganizationRepository.findByOrganizationId(organizationId);
    }

    @Override
    public List<UserOrganization> getMembershipsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuario no encontrado con id: " + userId);
        }
        return userOrganizationRepository.findByUserId(userId);
    }

    @Override
    public UserOrganization updateMemberRole(Long id, MembershipRole newRole) {
        if (newRole == null) {
            throw new BusinessRuleException("El nuevo rol no puede ser nulo");
        }
        UserOrganization uo = getMembershipById(id);
        uo.setRole(newRole);
        return userOrganizationRepository.save(uo);
    }

    @Override
    public UserOrganization deactivateMembership(Long id) {
        UserOrganization uo = getMembershipById(id);
        uo.setIsActive(false);
        return userOrganizationRepository.save(uo);
    }
}

