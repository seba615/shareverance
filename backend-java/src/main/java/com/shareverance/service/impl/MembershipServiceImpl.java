package com.shareverance.service.impl;

import com.shareverance.dto.MembershipRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.exception.ResourceNotFoundException;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.Organization;
import com.shareverance.model.User;
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
        if (request.userId() == null) {
            throw new ResourceNotFoundException("Usuario no encontrado con id: null");
        }
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado con id: " + request.userId()));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("No se puede incorporar un usuario inactivo");
        }
        if (request.organizationId() == null) {
            throw new ResourceNotFoundException("Organización no encontrada con id: null");
        }
        requireActiveArsOrganization(request.organizationId());
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
        UserOrganization membership = findMembership(id);
        requireActiveArsOrganization(membership.getOrganizationId());
        return membership;
    }

    @Override
    public List<UserOrganization> getMembershipsByOrganization(Long organizationId) {
        requireActiveArsOrganization(organizationId);
        return userOrganizationRepository.findByOrganizationId(organizationId);
    }

    @Override
    public List<UserOrganization> getMembershipsByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + userId));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("El usuario está inactivo");
        }
        return userOrganizationRepository.findByUserId(userId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public UserOrganization updateMemberRole(Long id, MembershipRole newRole) {
        if (newRole == null) {
            throw new BusinessRuleException("El nuevo rol no puede ser nulo");
        }
        UserOrganization uo = requireActiveMembershipForUpdate(id);
        if (uo.getRole() == MembershipRole.ADMIN && newRole != MembershipRole.ADMIN) {
            long activeAdmins = userOrganizationRepository.countActiveAdminsByOrganizationId(uo.getOrganizationId());
            if (activeAdmins <= 1) {
                throw new BusinessRuleException("No se puede revocar el rol al último ADMIN activo de la organización");
            }
        }
        uo.setRole(newRole);
        return userOrganizationRepository.save(uo);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public UserOrganization deactivateMembership(Long id) {
        UserOrganization uo = requireActiveMembershipForUpdate(id);
        if (uo.getRole() == MembershipRole.ADMIN) {
            long activeAdmins = userOrganizationRepository.countActiveAdminsByOrganizationId(uo.getOrganizationId());
            if (activeAdmins <= 1) {
                throw new BusinessRuleException("No se puede desactivar al último ADMIN activo de la organización");
            }
        }
        uo.setIsActive(false);
        return userOrganizationRepository.save(uo);
    }

    private UserOrganization requireActiveMembershipForUpdate(Long id) {
        UserOrganization initial = findMembership(id);
        // Orden común: organización antes que membresía, incluso si se cambia otro ADMIN.
        Organization organization = organizationRepository.findByIdForUpdate(initial.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organización no encontrada"));
        if (!Boolean.TRUE.equals(organization.getIsActive())
                || !"ARS".equalsIgnoreCase(organization.getCurrency())) {
            throw new BusinessRuleException("La organización debe estar activa y operar en ARS");
        }
        UserOrganization membership = userOrganizationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada con id: " + id));
        if (!organization.getId().equals(membership.getOrganizationId())) {
            throw new BusinessRuleException("La membresía cambió de organización");
        }
        if (!Boolean.TRUE.equals(membership.getIsActive())) {
            throw new BusinessRuleException("La membresía está inactiva");
        }
        return membership;
    }

    private UserOrganization requireActiveMembership(Long id) {
        UserOrganization membership = findMembership(id);
        requireActiveArsOrganization(membership.getOrganizationId());
        if (!Boolean.TRUE.equals(membership.getIsActive())) {
            throw new BusinessRuleException("La membresía está inactiva");
        }
        return membership;
    }

    private UserOrganization findMembership(Long id) {
        return userOrganizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada con id: " + id));
    }

    private Organization requireActiveArsOrganization(Long organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organización no encontrada con id: " + organizationId));
        if (!Boolean.TRUE.equals(organization.getIsActive())) {
            throw new BusinessRuleException("La organización está inactiva");
        }
        if (!"ARS".equalsIgnoreCase(organization.getCurrency())) {
            throw new BusinessRuleException("La única moneda admitida es ARS");
        }
        return organization;
    }
}
