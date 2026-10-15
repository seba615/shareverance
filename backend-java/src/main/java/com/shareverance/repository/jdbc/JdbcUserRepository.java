package com.shareverance.repository.jdbc;

import com.shareverance.model.User;
import com.shareverance.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getBoolean("is_active")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO users (full_name, email, password_hash, created_at, is_active)
                    VALUES (?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, TRUE))
                    RETURNING id, full_name, email, password_hash, created_at, is_active
                    """, ROW_MAPPER,
                    user.getFullName(), user.getEmail(), user.getPasswordHash(),
                    timestamp(user.getCreatedAt()), user.getIsActive());
        }

        return jdbcTemplate.queryForObject("""
                UPDATE users
                SET full_name = ?, email = ?, password_hash = ?, created_at = ?, is_active = ?
                WHERE id = ?
                RETURNING id, full_name, email, password_hash, created_at, is_active
                """, ROW_MAPPER,
                user.getFullName(), user.getEmail(), user.getPasswordHash(),
                timestamp(user.getCreatedAt()), user.getIsActive(), user.getId());
    }

    @Override
    public Optional<User> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT id, full_name, email, password_hash, created_at, is_active
                FROM users
                WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT id, full_name, email, password_hash, created_at, is_active
                FROM users
                WHERE LOWER(email) = LOWER(?)
                """, ROW_MAPPER, email).stream().findFirst();
    }

    @Override
    public List<User> findAll() {
        return jdbcTemplate.query("""
                SELECT id, full_name, email, password_hash, created_at, is_active
                FROM users
                ORDER BY id
                """, ROW_MAPPER);
    }

    @Override
    public boolean existsById(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE id = ?)", Boolean.class, id));
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE LOWER(email) = LOWER(?))",
                Boolean.class, email));
    }

    private static Timestamp timestamp(java.time.LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
