package com.shareverance.repository;

import com.shareverance.model.Expense;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {
    Expense save(Expense expense);
    Optional<Expense> findById(Long id);
    List<Expense> findByOrganizationId(Long organizationId);
    boolean existsById(Long id);
}

