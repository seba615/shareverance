package com.shareverance.repository.jdbc;

import com.shareverance.model.Organization;
import com.shareverance.model.OrganizationType;
import com.shareverance.repository.OrganizationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {

    private static final RowMapper<Organization> ROW_MAPPER = (rs, rowNum) -> new Organization(
            rs.getLong("id"),
            rs.getString("name"),
            OrganizationType.valueOf(rs.getString("type")),
            rs.getString("currency"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getBoolean("is_active")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Organization save(Organization organization) {
        if (organization.getId() == null) {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO organizations (name, type, currency, created_at, is_active)
                    VALUES (?, ?, COALESCE(?, 'ARS'), COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, TRUE))
                    RETURNING id, name, type, currency, created_at, is_active
                    """, ROW_MAPPER,
                    organization.getName(), organization.getType().name(), organization.getCurrency(),
                    timestamp(organization.getCreatedAt()), organization.getIsActive());
        }

        return jdbcTemplate.queryForObject("""
                UPDATE organizations
                SET name = ?, type = ?, currency = ?, created_at = ?, is_active = ?
                WHERE id = ?
                RETURNING id, name, type, currency, created_at, is_active
                """, ROW_MAPPER,
                organization.getName(), organization.getType().name(), organization.getCurrency(),
                timestamp(organization.getCreatedAt()), organization.getIsActive(), organization.getId());
    }

    @Override
    public Optional<Organization> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT id, name, type, currency, created_at, is_active
                FROM organizations
                WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Organization> findAll() {
        return jdbcTemplate.query("""
                SELECT id, name, type, currency, created_at, is_active
                FROM organizations
                ORDER BY id
                """, ROW_MAPPER);
    }

    @Override
    public boolean existsById(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM organizations WHERE id = ?)", Boolean.class, id));
    }

    private static Timestamp timestamp(java.time.LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
