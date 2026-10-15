package com.shareverance.repository.jdbc;

import com.shareverance.model.Category;
import com.shareverance.repository.CategoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCategoryRepository implements CategoryRepository {

    private static final RowMapper<Category> ROW_MAPPER = (rs, rowNum) -> new Category(
            rs.getLong("id"),
            rs.getLong("organization_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("color"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getBoolean("is_active")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcCategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Category save(Category category) {
        if (category.getId() == null) {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO categories (organization_id, name, description, color, created_at, is_active)
                    VALUES (?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, TRUE))
                    RETURNING id, organization_id, name, description, color, created_at, is_active
                    """, ROW_MAPPER,
                    category.getOrganizationId(), category.getName(), category.getDescription(),
                    category.getColor(), timestamp(category.getCreatedAt()), category.getIsActive());
        }

        return jdbcTemplate.queryForObject("""
                UPDATE categories
                SET organization_id = ?, name = ?, description = ?, color = ?, created_at = ?, is_active = ?
                WHERE id = ?
                RETURNING id, organization_id, name, description, color, created_at, is_active
                """, ROW_MAPPER,
                category.getOrganizationId(), category.getName(), category.getDescription(),
                category.getColor(), timestamp(category.getCreatedAt()), category.getIsActive(), category.getId());
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT id, organization_id, name, description, color, created_at, is_active
                FROM categories
                WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Category> findByOrganizationId(Long organizationId) {
        return jdbcTemplate.query("""
                SELECT id, organization_id, name, description, color, created_at, is_active
                FROM categories
                WHERE organization_id = ?
                ORDER BY id
                """, ROW_MAPPER, organizationId);
    }

    @Override
    public boolean existsById(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM categories WHERE id = ?)", Boolean.class, id));
    }

    private static Timestamp timestamp(java.time.LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
