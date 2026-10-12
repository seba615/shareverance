package com.shareverance;

import com.shareverance.dto.ExpenseCreateRequest;
import com.shareverance.dto.ExpenseDetailResponse;
import com.shareverance.dto.SplitMode;
import com.shareverance.repository.mock.*;
import com.shareverance.service.ExpenseService;
import com.shareverance.service.impl.ExpenseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseServiceTest {

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        var expenseRepo = new MockExpenseRepository();
        var expenseSplitRepo = new MockExpenseSplitRepository();
        var orgRepo = new MockOrganizationRepository();
        var userRepo = new MockUserRepository();
        var userOrgRepo = new MockUserOrganizationRepository();
        var categoryRepo = new MockCategoryRepository();

        expenseService = new ExpenseServiceImpl(
                expenseRepo,
                expenseSplitRepo,
                orgRepo,
                userRepo,
                userOrgRepo,
                categoryRepo
        );
    }

    @Test
    void testCreateExpenseWithAllMembersSplit() {
        // En los mocks iniciales:
        // Org 1 ("Consorcio Central") tiene 2 miembros: id 1 (Admin) e id 2 (User)
        ExpenseCreateRequest request = new ExpenseCreateRequest(
                1L,
                1L, // Created by user 1 (Admin)
                1L, // Paid by member 1
                1L, // Category 1
                new BigDecimal("100.00"),
                LocalDate.now(),
                "Factura de luz",
                null,
                SplitMode.ALL_MEMBERS,
                null,
                null
        );

        ExpenseDetailResponse response = expenseService.createExpense(request);

        assertNotNull(response);
        assertNotNull(response.expense().getId());
        assertEquals(new BigDecimal("100.00"), response.expense().getAmount());
        assertEquals(2, response.splits().size());
        assertEquals(new BigDecimal("50.00"), response.splits().get(0).getAmount());
        assertEquals(new BigDecimal("50.00"), response.splits().get(1).getAmount());
    }

    @Test
    void testCreateExpenseIndividualSplit() {
        ExpenseCreateRequest request = new ExpenseCreateRequest(
                1L,
                1L,
                1L,
                1L,
                new BigDecimal("500.00"),
                LocalDate.now(),
                "Gasto individual",
                null,
                SplitMode.INDIVIDUAL,
                null,
                null
        );

        ExpenseDetailResponse response = expenseService.createExpense(request);

        assertNotNull(response);
        assertEquals(1, response.splits().size());
        assertEquals(1L, response.splits().get(0).getUserOrganizationId());
        assertEquals(new BigDecimal("500.00"), response.splits().get(0).getAmount());
    }

    @Test
    void testRegularUserCannotPayForOthers() {
        // Usuario 2 es rol USER en Org 1. Intenta pagar en nombre de miembro 1 (ADMIN)
        ExpenseCreateRequest request = new ExpenseCreateRequest(
                1L,
                2L, // Creado por user 2
                1L, // Pagado por miembro 1 (que corresponde al user 1)
                1L,
                new BigDecimal("200.00"),
                LocalDate.now(),
                "Intento no autorizado",
                null,
                SplitMode.INDIVIDUAL,
                null,
                null
        );

        assertThrows(RuntimeException.class, () -> expenseService.createExpense(request));
    }

    @Test
    void testGetMonthlyHistoryContract() {
        var contract = expenseService.getMonthlyHistoryContract(1L, "2026-10");

        assertNotNull(contract);
        assertEquals("1", contract.contractVersion());
        assertNotNull(contract.requestId());
        assertEquals(1L, contract.organizationId());
        assertEquals("ARS", contract.currency());
        assertEquals("2026-10", contract.targetMonth());
        assertFalse(contract.categories().isEmpty());
        assertFalse(contract.history().isEmpty());

        // Verificamos que los meses sean 6 (abril a septiembre)
        assertEquals(6, contract.history().size());
        assertEquals("2026-04", contract.history().get(0).month());
        assertEquals("2026-09", contract.history().get(5).month());

        // Verificamos montos en centavos del mes de abril
        var aprilTotals = contract.history().get(0).categoryTotals();
        assertEquals(2, aprilTotals.size());
        assertEquals(10000000L, aprilTotals.get(0).amountMinor()); // 100000 * 100
        assertEquals(5000000L, aprilTotals.get(1).amountMinor());  // 50000 * 100
    }
}
