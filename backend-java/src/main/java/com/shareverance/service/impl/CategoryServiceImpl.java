package com.shareverance.service.impl;

import com.shareverance.dto.CategoryRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.exception.ResourceNotFoundException;
import com.shareverance.model.Category;
import com.shareverance.repository.CategoryRepository;
import com.shareverance.repository.OrganizationRepository;
import com.shareverance.service.CategoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final OrganizationRepository organizationRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository, OrganizationRepository organizationRepository) {
        this.categoryRepository = categoryRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public Category createCategory(Long organizationId, CategoryRequest request) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organización no encontrada con id: " + organizationId);
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessRuleException("El nombre de la categoría no puede estar vacío");
        }

        Category category = new Category(
                null,
                organizationId,
                request.name(),
                request.description(),
                request.color() != null ? request.color() : "#3B82F6",
                LocalDateTime.now(),
                true
        );

        return categoryRepository.save(category);
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id: " + id));
    }

    @Override
    public List<Category> getCategoriesByOrganization(Long organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException("Organización no encontrada con id: " + organizationId);
        }
        return categoryRepository.findByOrganizationId(organizationId);
    }

    @Override
    public Category updateCategory(Long id, CategoryRequest request) {
        Category category = getCategoryById(id);

        if (request.name() != null && !request.name().isBlank()) {
            category.setName(request.name());
        }
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.color() != null && !request.color().isBlank()) {
            category.setColor(request.color());
        }

        return categoryRepository.save(category);
    }

    @Override
    public Category deactivateCategory(Long id) {
        Category category = getCategoryById(id);
        category.setIsActive(false);
        return categoryRepository.save(category);
    }
}

