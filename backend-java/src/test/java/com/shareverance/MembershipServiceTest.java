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
}
