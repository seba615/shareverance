package com.shareverance;

import com.shareverance.dto.ExpenseCreateRequest;
import com.shareverance.dto.ExpenseDetailResponse;
import com.shareverance.dto.SplitMode;
import com.shareverance.exception.BusinessRuleException;
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
    private MockExpenseRepository expenseRepo;
    private MockExpenseSplitRepository expenseSplitRepo;
    private MockOrganizationRepository orgRepo;
    private MockUserRepository userRepo;
    private MockUserOrganizationRepository userOrgRepo;
    private MockCategoryRepository categoryRepo;

    @BeforeEach
    void setUp() {
        expenseRepo = new MockExpenseRepository();
        expenseSplitRepo = new MockExpenseSplitRepository();
        orgRepo = new MockOrganizationRepository();
        userRepo = new MockUserRepository();
        userOrgRepo = new MockUserOrganizationRepository();
        categoryRepo = new MockCategoryRepository();

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
    void refusesCreatingExpensesWhenOrganizationIsInactiveOrNotArs() {
        orgRepo.findById(1L).orElseThrow().setIsActive(false);
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(individualRequest()));

        orgRepo.findById(1L).orElseThrow().setIsActive(true);
        orgRepo.findById(1L).orElseThrow().setCurrency("USD");
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(individualRequest()));
    }

    @Test
    void refusesCreatingExpensesForInactiveUsersMembershipsOrCategories() {
        userRepo.findById(1L).orElseThrow().setIsActive(false);
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(individualRequest()));

        userRepo.findById(1L).orElseThrow().setIsActive(true);
        userOrgRepo.findById(1L).orElseThrow().setIsActive(false);
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(individualRequest()));

        userOrgRepo.findById(1L).orElseThrow().setIsActive(true);
        categoryRepo.findById(1L).orElseThrow().setIsActive(false);
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(individualRequest()));
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

    private static ExpenseCreateRequest individualRequest() {
        return new ExpenseCreateRequest(
                1L,
                1L,
                1L,
                1L,
                new BigDecimal("12.00"),
                LocalDate.now(),
                "Prueba",
                null,
                SplitMode.INDIVIDUAL,
                null,
                null
        );
    }
    private ExpenseCreateRequest equalRequest(String amount, SplitMode mode, java.util.List<Long> ids) {
        return new ExpenseCreateRequest(1L,1L,1L,1L,new BigDecimal(amount),LocalDate.now(),
                "Prueba reparto",null,mode,ids,null);
    }

    @Test void equalRemainderUsesAscendingIdsRegardlessOfInputOrder() {
        userOrgRepo.save(new com.shareverance.model.UserOrganization(17L,3L,1L,
                com.shareverance.model.MembershipRole.USER,java.time.LocalDateTime.now(),true));
        for (var ids : java.util.List.of(java.util.List.of(17L,2L,1L), java.util.List.of(1L,2L,17L))) {
            var result = expenseService.createExpense(equalRequest("0.05",SplitMode.SPECIFIC_GROUP,ids));
            assertEquals(java.util.List.of(1L,2L,17L),result.splits().stream()
                    .map(com.shareverance.model.ExpenseSplit::getUserOrganizationId).toList());
            assertEquals(java.util.List.of(new BigDecimal("0.02"),new BigDecimal("0.02"),new BigDecimal("0.01")),
                    result.splits().stream().map(com.shareverance.model.ExpenseSplit::getAmount).toList());
        }
    }

    @Test void rejectsInvalidParticipantsAndZeroParts() {
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(
                equalRequest("1.00",SplitMode.SPECIFIC_GROUP,java.util.List.of(2L,2L))));
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(
                equalRequest("1.00",SplitMode.SPECIFIC_GROUP,java.util.Arrays.asList(2L,null))));
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(
                equalRequest("1.00",SplitMode.SPECIFIC_GROUP,java.util.List.of(3L))));
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(
                equalRequest("0.01",SplitMode.ALL_MEMBERS,null)));
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(
                equalRequest("1.00",SplitMode.BETWEEN_TWO,java.util.List.of(1L))));
    }

    @Test void refusesPrecisionAndLimitsOnCreateAndUpdate() {
        var saved = expenseService.createExpense(equalRequest("1.00",SplitMode.INDIVIDUAL,null));
        for (String invalid : java.util.List.of("0","-1","1.001","10000000000.00")) {
            assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(
                    equalRequest(invalid,SplitMode.INDIVIDUAL,null)));
            assertThrows(BusinessRuleException.class, () -> expenseService.updateExpense(saved.expense().getId(),
                    new com.shareverance.dto.ExpenseUpdateRequest(null,new BigDecimal(invalid),null,null,null, 1)));
        }
        var maximum = expenseService.createExpense(equalRequest("9999999999.99",SplitMode.ALL_MEMBERS,null));
        assertEquals(new BigDecimal("9999999999.99"),maximum.splits().stream()
                .map(com.shareverance.model.ExpenseSplit::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    @Test void allMembersExcludesInactiveAccountsAndMemberships() {
        userRepo.findById(2L).orElseThrow().setIsActive(false);
        var result = expenseService.createExpense(equalRequest("1.00",SplitMode.ALL_MEMBERS,null));
        assertEquals(1,result.splits().size());
        userRepo.findById(2L).orElseThrow().setIsActive(true);
        userOrgRepo.findById(2L).orElseThrow().setIsActive(false);
        result = expenseService.createExpense(equalRequest("1.00",SplitMode.ALL_MEMBERS,null));
        assertEquals(1,result.splits().size());
        assertThrows(BusinessRuleException.class, () -> expenseService.createExpense(
                equalRequest("1.00",SplitMode.BETWEEN_TWO,java.util.List.of(2L))));
    }

    @Test
    void testOptimisticLockingConflictOnExpenseUpdate() {
        var created = expenseService.createExpense(equalRequest("100.00", SplitMode.INDIVIDUAL, null));
        assertEquals(1, created.expense().getVersion());

        // Actualización con versión correcta debe incrementar la versión
        var updated = expenseService.updateExpense(created.expense().getId(),
                new com.shareverance.dto.ExpenseUpdateRequest(null, new BigDecimal("120.00"), null, "Actualizado", null, 1));
        assertEquals(2, updated.expense().getVersion());
        assertEquals(new BigDecimal("120.00"), updated.expense().getAmount());

        // Intento concurrente con versión desactualizada (versión 1 cuando ya está en 2) debe lanzar 409 CONFLICT
        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                expenseService.updateExpense(created.expense().getId(),
                        new com.shareverance.dto.ExpenseUpdateRequest(null, new BigDecimal("150.00"), null, "Conflicto", null, 1)));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Conflicto de concurrencia"));
    }
}
