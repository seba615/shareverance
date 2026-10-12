package com.shareverance.repository.mock;

import com.shareverance.model.Expense;
import com.shareverance.repository.ExpenseRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MockExpenseRepository implements ExpenseRepository {

    private final Map<Long, Expense> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockExpenseRepository() {
        // Gasto demo en Org 1
        save(new Expense(
                null,
                1L, // orgId
                1L, // createdByUserId
                1L, // paidByMembershipId (Admin in Org 1)
                1L, // categoryId (Mantenimiento)
                new BigDecimal("15000.00"),
                LocalDate.now().minusDays(2),
                "Reparación ascensor",
                null,
                LocalDateTime.now().minusDays(2),
                true
        ));
    }

    @Override
    public Expense save(Expense expense) {
        if (expense.getId() == null) {
            expense.setId(idGenerator.getAndIncrement());
            if (expense.getCreatedAt() == null) {
                expense.setCreatedAt(LocalDateTime.now());
            }
            if (expense.getIsActive() == null) {
                expense.setIsActive(true);
            }
        }
        store.put(expense.getId(), expense);
        return expense;
    }

    @Override
    public Optional<Expense> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Expense> findByOrganizationId(Long organizationId) {
        return store.values().stream()
                .filter(e -> e.getOrganizationId().equals(organizationId))
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }
}

