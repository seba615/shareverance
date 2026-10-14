package com.shareverance.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shareverance.dto.ExpenseCreateRequest;
import com.shareverance.dto.ExpenseDetailResponse;
import com.shareverance.dto.ExpenseUpdateRequest;
import com.shareverance.model.Expense;
import com.shareverance.service.AuthService;
import com.shareverance.service.ExpenseService;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final AuthService authService;

    public ExpenseController(ExpenseService expenseService, AuthService authService) {
        this.expenseService = expenseService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<ExpenseDetailResponse> createExpense(@RequestBody ExpenseCreateRequest request,
                                                               HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (!authService.isOrganizationMember(userId, request.organizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }
        ExpenseCreateRequest authenticatedRequest = new ExpenseCreateRequest(
                request.organizationId(), userId, request.paidByMembershipId(), request.categoryId(),
                request.amount(), request.expenseDate(), request.description(), request.receiptUrl(),
                request.splitMode(), request.splitMemberIds(), request.customSplits());
        ExpenseDetailResponse created = expenseService.createExpense(authenticatedRequest);
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
