package com.shareverance.repository.jdbc;

import com.shareverance.model.MembershipRole;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.UserOrganizationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserOrganizationRepository implements UserOrganizationRepository {

    private static final RowMapper<UserOrganization> ROW_MAPPER = (rs, rowNum) -> {
        String userName = null;
        String userEmail = null;
        try {
            userName = rs.getString("full_name");
            userEmail = rs.getString("email");
        } catch (Exception ignored) {
            // columnas no disponibles en consultas sin join
        }
        return new UserOrganization(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("organization_id"),
                MembershipRole.valueOf(rs.getString("role")),
                rs.getTimestamp("joined_at").toLocalDateTime(),
                rs.getBoolean("is_active"),
                userName,
                userEmail
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserOrganizationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserOrganization save(UserOrganization userOrganization) {
        if (userOrganization.getId() == null) {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO user_organization (user_id, organization_id, role, joined_at, is_active)
                    VALUES (?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, TRUE))
                    RETURNING id, user_id, organization_id, role, joined_at, is_active
                    """, ROW_MAPPER,
                    userOrganization.getUserId(), userOrganization.getOrganizationId(),
                    userOrganization.getRole().name(), timestamp(userOrganization.getJoinedAt()),
                    userOrganization.getIsActive());
        }

        return jdbcTemplate.queryForObject("""
                UPDATE user_organization
                SET user_id = ?, organization_id = ?, role = ?, joined_at = ?, is_active = ?
                WHERE id = ?
                RETURNING id, user_id, organization_id, role, joined_at, is_active
                """, ROW_MAPPER,
                userOrganization.getUserId(), userOrganization.getOrganizationId(),
                userOrganization.getRole().name(), timestamp(userOrganization.getJoinedAt()),
                userOrganization.getIsActive(), userOrganization.getId());
    }

    @Override
    public Optional<UserOrganization> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT uo.id, uo.user_id, uo.organization_id, uo.role, uo.joined_at, uo.is_active,
                       u.full_name, u.email
                FROM user_organization uo
                LEFT JOIN users u ON u.id = uo.user_id
                WHERE uo.id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<UserOrganization> findByIdForUpdate(Long id) {
        return jdbcTemplate.query("""
                SELECT id, user_id, organization_id, role, joined_at, is_active
                FROM user_organization
                WHERE id = ?
                FOR UPDATE
                """, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public long countActiveAdminsByOrganizationId(Long organizationId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM user_organization
                WHERE organization_id = ? AND role = 'ADMIN' AND is_active = TRUE
                """, Long.class, organizationId);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<UserOrganization> findByUserIdAndOrganizationId(Long userId, Long organizationId) {
        return jdbcTemplate.query("""
                SELECT uo.id, uo.user_id, uo.organization_id, uo.role, uo.joined_at, uo.is_active,
                       u.full_name, u.email
                FROM user_organization uo
                LEFT JOIN users u ON u.id = uo.user_id
                WHERE uo.user_id = ? AND uo.organization_id = ?
                """, ROW_MAPPER, userId, organizationId).stream().findFirst();
    }

    @Override
    public List<UserOrganization> findByOrganizationId(Long organizationId) {
        return jdbcTemplate.query("""
                SELECT uo.id, uo.user_id, uo.organization_id, uo.role, uo.joined_at, uo.is_active,
                       u.full_name, u.email
                FROM user_organization uo
                LEFT JOIN users u ON u.id = uo.user_id
                WHERE uo.organization_id = ?
                ORDER BY uo.id
                """, ROW_MAPPER, organizationId);
    }

    @Override
    public List<UserOrganization> findByUserId(Long userId) {
        return jdbcTemplate.query("""
                SELECT uo.id, uo.user_id, uo.organization_id, uo.role, uo.joined_at, uo.is_active,
                       u.full_name, u.email
                FROM user_organization uo
                LEFT JOIN users u ON u.id = uo.user_id
                WHERE uo.user_id = ?
                ORDER BY uo.id
                """, ROW_MAPPER, userId);
    }

    @Override
    public boolean existsById(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_organization WHERE id = ?)", Boolean.class, id));
    }

    @Override
    public boolean existsByUserIdAndOrganizationId(Long userId, Long organizationId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM user_organization WHERE user_id = ? AND organization_id = ?
                )
                """, Boolean.class, userId, organizationId));
    }

    private static Timestamp timestamp(java.time.LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
