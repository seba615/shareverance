package com.shareverance.service;

import com.shareverance.dto.ExpenseCreateRequest;
import com.shareverance.dto.ExpenseDetailResponse;
import com.shareverance.dto.ExpenseUpdateRequest;
import com.shareverance.model.Expense;

import java.util.List;

public interface ExpenseService {
    ExpenseDetailResponse createExpense(ExpenseCreateRequest request);
    ExpenseDetailResponse getExpenseById(Long id);
    List<ExpenseDetailResponse> getExpensesByOrganization(Long organizationId);
    ExpenseDetailResponse updateExpense(Long id, ExpenseUpdateRequest request);
    Expense deactivateExpense(Long id);
    com.shareverance.dto.contract.HistoryContractResponse getMonthlyHistoryContract(Long organizationId, String targetMonth);
}

