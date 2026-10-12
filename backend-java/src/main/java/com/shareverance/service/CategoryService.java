package com.shareverance.service;

import com.shareverance.dto.CategoryRequest;
import com.shareverance.model.Category;

import java.util.List;

public interface CategoryService {
    Category createCategory(Long organizationId, CategoryRequest request);
    Category getCategoryById(Long id);
    List<Category> getCategoriesByOrganization(Long organizationId);
    Category updateCategory(Long id, CategoryRequest request);
    Category deactivateCategory(Long id);
}

