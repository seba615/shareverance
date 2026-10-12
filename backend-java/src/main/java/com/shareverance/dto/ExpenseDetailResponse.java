package com.shareverance.dto;

import com.shareverance.model.Expense;
import com.shareverance.model.ExpenseSplit;

import java.util.List;

public record ExpenseDetailResponse(
    Expense expense,
    List<ExpenseSplit> splits
) {}

