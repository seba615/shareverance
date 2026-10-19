package com.shareverance.repository.mock;

import com.shareverance.model.Expense;
import com.shareverance.repository.ExpenseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockExpenseRepository implements ExpenseRepository {

    private final Map<Long, Expense> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockExpenseRepository() {
        // Gasto reciente
        save(new Expense(
                null, 1L, 1L, 1L, 1L,
                new BigDecimal("15000.00"),
                LocalDate.now().minusDays(2),
                "Reparación ascensor",
                null,
                LocalDateTime.now().minusDays(2),
                true
        ));

        // Historial de 6 meses cerrados previos (Abril a Septiembre 2026) según ejemplo de CONTRATO_PYTHON_V1
        // Categoría 1: Mantenimiento (id 1)
        // Categoría 2: Servicios (id 2)
        long[][] monthlyData = {
                {4, 100000, 50000},
                {5, 105000, 50000},
                {6, 110000, 50000},
                {7, 115000, 50000},
                {8, 120000, 50000},
                {9, 125000, 50000}
        };

        for (long[] data : monthlyData) {
            int month = (int) data[0];
            BigDecimal cat1Amount = BigDecimal.valueOf(data[1]);
            BigDecimal cat2Amount = BigDecimal.valueOf(data[2]);

            save(new Expense(
                    null, 1L, 1L, 1L, 1L,
                    cat1Amount,
                    LocalDate.of(2026, month, 15),
                    "Mantenimiento mes " + month,
                    null,
                    LocalDateTime.of(2026, month, 15, 10, 0),
                    true
            ));

            save(new Expense(
                    null, 1L, 1L, 1L, 2L,
                    cat2Amount,
                    LocalDate.of(2026, month, 18),
                    "Servicios mes " + month,
                    null,
                    LocalDateTime.of(2026, month, 18, 11, 0),
                    true
            ));
        }
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
    public Optional<Expense> updateWithOptimisticLocking(Expense expense, Integer expectedVersion) {
        Expense existing = store.get(expense.getId());
        if (existing == null || !Boolean.TRUE.equals(existing.getIsActive()) || !existing.getVersion().equals(expectedVersion)) {
            return Optional.empty();
        }
        expense.setVersion(existing.getVersion() + 1);
        store.put(expense.getId(), expense);
        return Optional.of(expense);
    }

    @Override
    public Optional<Expense> deactivateById(Long id) {
        Expense existing = store.get(id);
        if (existing == null || !Boolean.TRUE.equals(existing.getIsActive())) return Optional.empty();
        existing.setIsActive(false);
        existing.setVersion(existing.getVersion() + 1);
        return Optional.of(existing);
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
    public PageResult findPage(Long organizationId, com.shareverance.dto.ExpenseFilter filter, int page, int size) {
        var rows = findByOrganizationId(organizationId).stream()
                .filter(e -> filter.fromDate() == null || !e.getExpenseDate().isBefore(filter.fromDate()))
                .filter(e -> filter.toDate() == null || !e.getExpenseDate().isAfter(filter.toDate()))
                .filter(e -> filter.categoryId() == null || filter.categoryId().equals(e.getCategoryId()))
                .filter(e -> "ALL".equals(filter.state()) || Boolean.TRUE.equals(e.getIsActive()) == "ACTIVE".equals(filter.state()))
                .sorted(java.util.Comparator.comparing(Expense::getExpenseDate).thenComparing(Expense::getId).reversed()).toList();
        var total = rows.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PageResult(rows.stream().skip((long) page * size).limit(size).toList(), rows.size(), total);
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }
}
