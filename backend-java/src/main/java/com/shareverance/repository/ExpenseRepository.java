package com.shareverance.repository;

import com.shareverance.model.Expense;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {
    record PageResult(List<Expense> expenses, long totalElements, java.math.BigDecimal totalAmount) {}
    PageResult findPage(Long organizationId, com.shareverance.dto.ExpenseFilter filter, int page, int size);
    List<com.shareverance.dto.ExpenseCalendarDayResponse> findCalendarDays(
            Long organizationId, java.time.LocalDate fromDate, java.time.LocalDate toDate);
    Expense save(Expense expense);
    Optional<Expense> updateWithOptimisticLocking(Expense expense, Integer expectedVersion);
    Optional<Expense> findById(Long id);
    Optional<Expense> deactivateById(Long id);
    List<Expense> findByOrganizationId(Long organizationId);
    boolean existsById(Long id);
}

