package com.shareverance.controller;

import com.shareverance.dto.ExpenseCreateRequest;
import com.shareverance.dto.ExpenseDetailResponse;
import com.shareverance.dto.ExpenseUpdateRequest;
import com.shareverance.model.Expense;
import com.shareverance.service.ExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseDetailResponse> createExpense(@RequestBody ExpenseCreateRequest request) {
        ExpenseDetailResponse created = expenseService.createExpense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseDetailResponse> getExpenseById(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getExpenseById(id));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<ExpenseDetailResponse>> getExpensesByOrganization(@PathVariable Long organizationId) {
        return ResponseEntity.ok(expenseService.getExpensesByOrganization(organizationId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseDetailResponse> updateExpense(@PathVariable Long id, @RequestBody ExpenseUpdateRequest request) {
        return ResponseEntity.ok(expenseService.updateExpense(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Expense> deactivateExpense(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.deactivateExpense(id));
    }

    @GetMapping("/organization/{organizationId}/history")
    public ResponseEntity<com.shareverance.dto.contract.HistoryContractResponse> getOrganizationHistoryContract(
            @PathVariable Long organizationId,
            @RequestParam(required = false) String targetMonth
    ) {
        return ResponseEntity.ok(expenseService.getMonthlyHistoryContract(organizationId, targetMonth));
    }
}

