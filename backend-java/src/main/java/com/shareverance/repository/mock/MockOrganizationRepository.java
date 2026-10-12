package com.shareverance.repository.mock;

import com.shareverance.model.Organization;
import com.shareverance.model.OrganizationType;
import com.shareverance.repository.OrganizationRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MockOrganizationRepository implements OrganizationRepository {

    private final Map<Long, Organization> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockOrganizationRepository() {
        // Inicializar datos demo
        save(new Organization(null, "Consorcio Central", OrganizationType.CONDO, "ARS", LocalDateTime.now(), true));
        save(new Organization(null, "Hogar Pérez", OrganizationType.HOUSEHOLD, "ARS", LocalDateTime.now(), true));
    }

    @Override
    public Organization save(Organization organization) {
        if (organization.getId() == null) {
            organization.setId(idGenerator.getAndIncrement());
            if (organization.getCreatedAt() == null) {
                organization.setCreatedAt(LocalDateTime.now());
            }
            if (organization.getIsActive() == null) {
                organization.setIsActive(true);
            }
            if (organization.getCurrency() == null) {
                organization.setCurrency("ARS");
            }
        }
        store.put(organization.getId(), organization);
        return organization;
    }

    @Override
    public Optional<Organization> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Organization> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }
}

