package com.shareverance;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.shareverance.dto.MembershipRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.repository.mock.MockOrganizationRepository;
import com.shareverance.repository.mock.MockUserOrganizationRepository;
import com.shareverance.repository.mock.MockUserRepository;
import com.shareverance.service.impl.MembershipServiceImpl;

class MembershipServiceTest {

    @Test
    void rejectsInactiveUsersAndOrganizationsWhenAddingMembers() {
        var memberships = new MockUserOrganizationRepository();
        var users = new MockUserRepository();
        var organizations = new MockOrganizationRepository();
        var service = new MembershipServiceImpl(memberships, users, organizations);

        users.findById(3L).orElseThrow().setIsActive(false);
        assertThrows(BusinessRuleException.class, () ->
                service.addMemberToOrganization(new MembershipRequest(3L, 1L, null)));

        users.findById(3L).orElseThrow().setIsActive(true);
        organizations.findById(1L).orElseThrow().setIsActive(false);
        assertThrows(BusinessRuleException.class, () ->
                service.addMemberToOrganization(new MembershipRequest(3L, 1L, null)));
    }

    @Test
    void cannotDeactivateOrDemoteLastActiveAdmin() {
        var memberships = new MockUserOrganizationRepository();
        var users = new MockUserRepository();
        var organizations = new MockOrganizationRepository();
        var service = new MembershipServiceImpl(memberships, users, organizations);

        // Org 1 tiene membership 1 (User 1, ADMIN) y membership 2 (User 2, USER)
        var exDeactivate = assertThrows(BusinessRuleException.class, () -> service.deactivateMembership(1L));
        org.junit.jupiter.api.Assertions.assertTrue(exDeactivate.getMessage().contains("último ADMIN activo"));

        var exDemote = assertThrows(BusinessRuleException.class, () -> service.updateMemberRole(1L, com.shareverance.model.MembershipRole.USER));
        org.junit.jupiter.api.Assertions.assertTrue(exDemote.getMessage().contains("último ADMIN activo"));

        // Si agregamos un segundo ADMIN a Org 1, ahora sí se puede desactivar o cambiar de rol al primero
        memberships.save(new com.shareverance.model.UserOrganization(null, 3L, 1L, com.shareverance.model.MembershipRole.ADMIN, java.time.LocalDateTime.now(), true));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.updateMemberRole(1L, com.shareverance.model.MembershipRole.USER));
    }

    @Test
    void canAddMemberByExactEmail() {
        var memberships = new MockUserOrganizationRepository();
        var users = new MockUserRepository();
        var organizations = new MockOrganizationRepository();
        var service = new MembershipServiceImpl(memberships, users, organizations);

        // User 3 (maria@gomez.com) no está en Org 1
        var created = service.addMemberToOrganization(
                new MembershipRequest(null, 1L, com.shareverance.model.MembershipRole.USER, "maria@gomez.com"));
        org.junit.jupiter.api.Assertions.assertNotNull(created.getId());
        org.junit.jupiter.api.Assertions.assertEquals(3L, created.getUserId());
        org.junit.jupiter.api.Assertions.assertEquals(1L, created.getOrganizationId());
        org.junit.jupiter.api.Assertions.assertEquals("María Gómez", created.getUserName());
        org.junit.jupiter.api.Assertions.assertEquals("maria@gomez.com", created.getUserEmail());

        // Intentar agregar email inexistente lanza ResourceNotFoundException
        org.junit.jupiter.api.Assertions.assertThrows(com.shareverance.exception.ResourceNotFoundException.class, () ->
                service.addMemberToOrganization(
                        new MembershipRequest(null, 1L, com.shareverance.model.MembershipRole.USER, "inexistente@example.invalid")));
    }

    @Test
    void canReactivateDeactivatedMembership() {
        var memberships = new MockUserOrganizationRepository();
        var users = new MockUserRepository();
        var organizations = new MockOrganizationRepository();
        var service = new MembershipServiceImpl(memberships, users, organizations);

        // En Org 1, membership 2 (User 2, USER) se desactiva
        var deactivated = service.deactivateMembership(2L);
        org.junit.jupiter.api.Assertions.assertFalse(deactivated.getIsActive());

        // Reactivar mediante método explícito
        var reactivated = service.reactivateMembership(2L);
        org.junit.jupiter.api.Assertions.assertTrue(reactivated.getIsActive());
        org.junit.jupiter.api.Assertions.assertEquals(2L, reactivated.getId());

        // Volver a desactivar y reactivar mediante addMemberToOrganization por email
        service.deactivateMembership(2L);
        var readded = service.addMemberToOrganization(
                new MembershipRequest(null, 1L, com.shareverance.model.MembershipRole.USER, "juan@perez.com"));
        org.junit.jupiter.api.Assertions.assertTrue(readded.getIsActive());
        org.junit.jupiter.api.Assertions.assertEquals(2L, readded.getId());
    }
}
