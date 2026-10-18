package com.shareverance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.shareverance.dto.OrganizationRequest;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.Organization;
import com.shareverance.model.OrganizationType;
import com.shareverance.model.UserOrganization;
import com.shareverance.exception.BusinessRuleException;
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
                new OrganizationRequest("Hogar", OrganizationType.HOUSEHOLD, "ars"), 7L);

        assertEquals(12L, created.getId());
        assertEquals("ARS", created.getCurrency());
        var membershipCaptor = org.mockito.ArgumentCaptor.forClass(UserOrganization.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertEquals(7L, membershipCaptor.getValue().getUserId());
        assertEquals(12L, membershipCaptor.getValue().getOrganizationId());
        assertEquals(MembershipRole.ADMIN, membershipCaptor.getValue().getRole());
    }

    @Test
    void rejectsCurrenciesOtherThanArsOnCreationAndUpdate() {
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        UserOrganizationRepository membershipRepository = mock(UserOrganizationRepository.class);
        OrganizationServiceImpl service = new OrganizationServiceImpl(
                organizationRepository, membershipRepository);

        assertThrows(BusinessRuleException.class, () -> service.createOrganization(
                new OrganizationRequest("Hogar", OrganizationType.HOUSEHOLD, "USD"), 7L));

        Organization existing = new Organization(12L, "Hogar", OrganizationType.HOUSEHOLD,
                "ARS", java.time.LocalDateTime.now(), true);
        when(organizationRepository.findById(12L)).thenReturn(java.util.Optional.of(existing));
        assertThrows(BusinessRuleException.class, () -> service.updateOrganization(12L,
                new OrganizationRequest(null, null, "USD")));
        verify(organizationRepository, never()).save(any(Organization.class));
    }

    @Test
    void excludesInactiveAndNonArsOrganizationsFromUserOrganizationList() {
        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        UserOrganizationRepository membershipRepository = mock(UserOrganizationRepository.class);
        Organization activeArs = new Organization(1L, "Active", OrganizationType.HOUSEHOLD,
                "ARS", java.time.LocalDateTime.now(), true);
        Organization inactive = new Organization(2L, "Inactive", OrganizationType.HOUSEHOLD,
                "ARS", java.time.LocalDateTime.now(), false);
        Organization unsupportedCurrency = new Organization(3L, "Unsupported", OrganizationType.HOUSEHOLD,
                "USD", java.time.LocalDateTime.now(), true);
        when(membershipRepository.findByUserId(7L)).thenReturn(java.util.List.of(
                new UserOrganization(1L, 7L, 1L, MembershipRole.ADMIN, null, true),
                new UserOrganization(2L, 7L, 2L, MembershipRole.ADMIN, null, true),
                new UserOrganization(3L, 7L, 3L, MembershipRole.ADMIN, null, true)));
        when(organizationRepository.findById(1L)).thenReturn(java.util.Optional.of(activeArs));
        when(organizationRepository.findById(2L)).thenReturn(java.util.Optional.of(inactive));
        when(organizationRepository.findById(3L)).thenReturn(java.util.Optional.of(unsupportedCurrency));
        OrganizationServiceImpl service = new OrganizationServiceImpl(
                organizationRepository, membershipRepository);

        assertEquals(java.util.List.of(activeArs), service.getOrganizationsForUser(7L));
    }
}
