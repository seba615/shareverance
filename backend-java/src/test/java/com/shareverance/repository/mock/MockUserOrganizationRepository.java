package com.shareverance.repository.mock;

import com.shareverance.model.MembershipRole;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.UserOrganizationRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockUserOrganizationRepository implements UserOrganizationRepository {

    private final Map<Long, UserOrganization> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockUserOrganizationRepository() {
        // En org 1 (Consorcio Central):
        // User 1 es ADMIN (id 1)
        // User 2 es USER (id 2)
        save(new UserOrganization(null, 1L, 1L, MembershipRole.ADMIN, LocalDateTime.now(), true));
        save(new UserOrganization(null, 2L, 1L, MembershipRole.USER, LocalDateTime.now(), true));

        // En org 2 (Hogar Pérez):
        // User 2 es ADMIN (id 3)
        // User 3 es USER (id 4)
        save(new UserOrganization(null, 2L, 2L, MembershipRole.ADMIN, LocalDateTime.now(), true));
        save(new UserOrganization(null, 3L, 2L, MembershipRole.USER, LocalDateTime.now(), true));
    }

    @Override
    public UserOrganization save(UserOrganization userOrganization) {
        if (userOrganization.getId() == null) {
            userOrganization.setId(idGenerator.getAndIncrement());
            if (userOrganization.getJoinedAt() == null) {
                userOrganization.setJoinedAt(LocalDateTime.now());
            }
            if (userOrganization.getIsActive() == null) {
                userOrganization.setIsActive(true);
            }
        }
        store.put(userOrganization.getId(), userOrganization);
        return userOrganization;
    }

    @Override
    public Optional<UserOrganization> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<UserOrganization> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public long countActiveAdminsByOrganizationId(Long organizationId) {
        return store.values().stream()
                .filter(uo -> uo.getOrganizationId().equals(organizationId))
                .filter(uo -> uo.getRole() == MembershipRole.ADMIN)
                .filter(uo -> Boolean.TRUE.equals(uo.getIsActive()))
                .count();
    }

    @Override
    public Optional<UserOrganization> findByUserIdAndOrganizationId(Long userId, Long organizationId) {
        return store.values().stream()
                .filter(uo -> uo.getUserId().equals(userId) && uo.getOrganizationId().equals(organizationId))
                .findFirst();
    }

    @Override
    public List<UserOrganization> findByOrganizationId(Long organizationId) {
        return store.values().stream()
                .filter(uo -> uo.getOrganizationId().equals(organizationId))
                .toList();
    }

    @Override
    public List<UserOrganization> findByUserId(Long userId) {
        return store.values().stream()
                .filter(uo -> uo.getUserId().equals(userId))
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    @Override
    public boolean existsByUserIdAndOrganizationId(Long userId, Long organizationId) {
        return findByUserIdAndOrganizationId(userId, organizationId).isPresent();
    }
}
