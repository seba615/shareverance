package com.shareverance.repository.mock;

import com.shareverance.model.ExpenseSplit;
import com.shareverance.repository.ExpenseSplitRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MockExpenseSplitRepository implements ExpenseSplitRepository {

    private final Map<Long, ExpenseSplit> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockExpenseSplitRepository() {
        // Splits demo para el gasto 1 (15000 dividido en partes iguales entre miembro 1 y miembro 2)
        save(new ExpenseSplit(null, 1L, 1L, new BigDecimal("7500.00")));
        save(new ExpenseSplit(null, 1L, 2L, new BigDecimal("7500.00")));
    }

    @Override
    public ExpenseSplit save(ExpenseSplit expenseSplit) {
        if (expenseSplit.getId() == null) {
            expenseSplit.setId(idGenerator.getAndIncrement());
        }
        store.put(expenseSplit.getId(), expenseSplit);
        return expenseSplit;
    }

    @Override
    public List<ExpenseSplit> saveAll(List<ExpenseSplit> splits) {
        List<ExpenseSplit> saved = new ArrayList<>();
        for (ExpenseSplit split : splits) {
            saved.add(save(split));
        }
        return saved;
    }

    @Override
    public List<ExpenseSplit> findByExpenseId(Long expenseId) {
        return store.values().stream()
                .filter(s -> s.getExpenseId().equals(expenseId))
                .toList();
    }

    @Override
    public void deleteByExpenseId(Long expenseId) {
        store.entrySet().removeIf(entry -> entry.getValue().getExpenseId().equals(expenseId));
    }
}
