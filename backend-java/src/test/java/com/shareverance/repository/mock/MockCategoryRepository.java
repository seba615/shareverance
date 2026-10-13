package com.shareverance.repository.mock;

import com.shareverance.model.Category;
import com.shareverance.repository.CategoryRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockCategoryRepository implements CategoryRepository {

    private final Map<Long, Category> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockCategoryRepository() {
        // En org 1 (Consorcio)
        save(new Category(null, 1L, "Mantenimiento", "Reparaciones y limpieza", "#3B82F6", LocalDateTime.now(), true));
        save(new Category(null, 1L, "Servicios", "Luz, gas, agua", "#10B981", LocalDateTime.now(), true));

        // En org 2 (Hogar)
        save(new Category(null, 2L, "Supermercado", "Comida y víveres", "#EF4444", LocalDateTime.now(), true));
        save(new Category(null, 2L, "Servicios", "Internet y streaming", "#F59E0B", LocalDateTime.now(), true));
    }

    @Override
    public Category save(Category category) {
        if (category.getId() == null) {
            category.setId(idGenerator.getAndIncrement());
            if (category.getCreatedAt() == null) {
                category.setCreatedAt(LocalDateTime.now());
            }
            if (category.getIsActive() == null) {
                category.setIsActive(true);
            }
        }
        store.put(category.getId(), category);
        return category;
    }

    @Override
    public Optional<Category> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Category> findByOrganizationId(Long organizationId) {
        return store.values().stream()
                .filter(c -> c.getOrganizationId().equals(organizationId))
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }
}
