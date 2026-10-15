package com.shareverance.repository;

import java.time.Instant;
import java.util.Optional;

public interface SessionRepository {
    void save(String tokenHash, Long userId, Instant expiresAt);
    Optional<Long> findActiveUserId(String tokenHash, Instant now);
    void delete(String tokenHash);
    void deleteExpired(Instant now);
}
