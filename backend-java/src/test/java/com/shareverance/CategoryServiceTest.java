package com.shareverance;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.shareverance.dto.CategoryRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.repository.mock.MockCategoryRepository;
import com.shareverance.repository.mock.MockOrganizationRepository;
import com.shareverance.service.impl.CategoryServiceImpl;

class CategoryServiceTest {

    @Test
    void rejectsCategoryOperationsForInactiveOrNonArsOrganizations() {
        var categories = new MockCategoryRepository();
        var organizations = new MockOrganizationRepository();
        var service = new CategoryServiceImpl(categories, organizations);

        organizations.findById(1L).orElseThrow().setIsActive(false);
        assertThrows(BusinessRuleException.class, () ->
                service.createCategory(1L, new CategoryRequest("Nueva", null, null)));

        organizations.findById(1L).orElseThrow().setIsActive(true);
        organizations.findById(1L).orElseThrow().setCurrency("USD");
        assertThrows(BusinessRuleException.class, () ->
                service.getCategoriesByOrganization(1L));
    }
}
