package com.shareverance.repository;

import com.shareverance.model.ExpenseSplit;

import java.util.List;

public interface ExpenseSplitRepository {
    ExpenseSplit save(ExpenseSplit expenseSplit);
    List<ExpenseSplit> saveAll(List<ExpenseSplit> splits);
    List<ExpenseSplit> findByExpenseId(Long expenseId);
    void deleteByExpenseId(Long expenseId);
}

