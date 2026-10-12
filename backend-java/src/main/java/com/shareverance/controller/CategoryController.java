package com.shareverance.controller;

import com.shareverance.dto.CategoryRequest;
import com.shareverance.model.Category;
import com.shareverance.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/organization/{organizationId}")
    public ResponseEntity<Category> createCategory(@PathVariable Long organizationId, @RequestBody CategoryRequest request) {
        Category created = categoryService.createCategory(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<Category>> getCategoriesByOrganization(@PathVariable Long organizationId) {
        return ResponseEntity.ok(categoryService.getCategoriesByOrganization(organizationId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Category> deactivateCategory(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.deactivateCategory(id));
    }
}

