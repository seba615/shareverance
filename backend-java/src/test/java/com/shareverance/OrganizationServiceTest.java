package com.shareverance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.shareverance.dto.OrganizationRequest;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.Organization;
import com.shareverance.model.OrganizationType;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.OrganizationRepository;
import com.shareverance.repository.UserOrganizationRepository;
import com.shareverance.service.impl.OrganizationServiceImpl;

class OrganizationServiceTest {

    @Test
    void organizationCreatorIsMadeAdmin() {
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        UserOrganizationRepository membershipRepository = mock(UserOrganizationRepository.class);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization organization = invocation.getArgument(0);
            organization.setId(12L);
            return organization;
        });
        OrganizationServiceImpl service = new OrganizationServiceImpl(
                organizationRepository, membershipRepository);

        Organization created = service.createOrganization(
                new OrganizationRequest("Hogar", OrganizationType.HOUSEHOLD, "ARS"), 7L);

        assertEquals(12L, created.getId());
        var membershipCaptor = org.mockito.ArgumentCaptor.forClass(UserOrganization.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertEquals(7L, membershipCaptor.getValue().getUserId());
        assertEquals(12L, membershipCaptor.getValue().getOrganizationId());
        assertEquals(MembershipRole.ADMIN, membershipCaptor.getValue().getRole());
    }
}
