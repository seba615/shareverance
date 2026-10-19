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

    @GetMapping("/organization/{organizationId}/page")
    public ResponseEntity<com.shareverance.dto.ExpensePageResponse> getPage(@PathVariable Long organizationId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate toDate,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "ACTIVE") String state,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(expenseService.getExpensePage(organizationId,
                new com.shareverance.dto.ExpenseFilter(fromDate, toDate, categoryId, state), page, size));
    }

    @GetMapping("/organization/{organizationId}/calendar")
    public ResponseEntity<com.shareverance.dto.ExpenseCalendarResponse> getCalendar(
            @PathVariable Long organizationId,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(expenseService.getExpenseCalendar(organizationId, month));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseDetailResponse> updateExpense(@PathVariable Long id, @RequestBody ExpenseUpdateRequest request) {
        return ResponseEntity.ok(expenseService.updateExpense(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Expense> deactivateExpense(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.deactivateExpense(id));
    }
}
