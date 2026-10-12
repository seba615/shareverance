package com.shareverance.repository;

import com.shareverance.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Category save(Category category);
    Optional<Category> findById(Long id);
    List<Category> findByOrganizationId(Long organizationId);
    boolean existsById(Long id);
}

