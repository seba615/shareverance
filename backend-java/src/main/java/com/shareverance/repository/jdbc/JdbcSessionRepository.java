package com.shareverance.repository.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.shareverance.repository.SessionRepository;

@Repository
public class JdbcSessionRepository implements SessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(String tokenHash, Long userId, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO auth_sessions (token_hash, user_id, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT (token_hash) DO UPDATE SET expires_at = EXCLUDED.expires_at
                """, tokenHash, userId, Timestamp.from(expiresAt));
    }

    @Override
    public Optional<Long> findActiveUserId(String tokenHash, Instant now) {
        List<Long> userIds = jdbcTemplate.query("""
                SELECT user_id FROM auth_sessions
                WHERE token_hash = ? AND expires_at > ?
                """, (rs, rowNum) -> rs.getLong("user_id"), tokenHash, Timestamp.from(now));
        return userIds.stream().findFirst();
    }

    @Override
    public void delete(String tokenHash) {
        jdbcTemplate.update("DELETE FROM auth_sessions WHERE token_hash = ?", tokenHash);
    }

    @Override
    public void deleteExpired(Instant now) {
        jdbcTemplate.update("DELETE FROM auth_sessions WHERE expires_at <= ?", Timestamp.from(now));
    }
}
