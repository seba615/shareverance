package com.shareverance.service.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shareverance.dto.ExpenseCreateRequest;
import com.shareverance.dto.ExpenseDetailResponse;
import com.shareverance.dto.ExpenseSplitRequest;
import com.shareverance.dto.ExpenseUpdateRequest;
import com.shareverance.dto.SplitMode;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.exception.ResourceNotFoundException;
import com.shareverance.model.Category;
import com.shareverance.model.Expense;
import com.shareverance.model.ExpenseSplit;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.Organization;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.CategoryRepository;
import com.shareverance.repository.ExpenseRepository;
import com.shareverance.repository.ExpenseSplitRepository;
import com.shareverance.repository.OrganizationRepository;
import com.shareverance.repository.UserOrganizationRepository;
import com.shareverance.repository.UserRepository;
import com.shareverance.service.ExpenseService;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final CategoryRepository categoryRepository;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                              ExpenseSplitRepository expenseSplitRepository,
                              OrganizationRepository organizationRepository,
                              UserRepository userRepository,
                              UserOrganizationRepository userOrganizationRepository,
                              CategoryRepository categoryRepository) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public ExpenseDetailResponse createExpense(ExpenseCreateRequest request) {
        // Validar campos obligatorios
        if (request.organizationId() == null) {
            throw new BusinessRuleException("La organización es obligatoria");
        }
        if (request.createdByUserId() == null) {
            throw new BusinessRuleException("El usuario creador es obligatorio");
        }
        if (request.paidByMembershipId() == null) {
            throw new BusinessRuleException("El miembro pagador es obligatorio");
        }
        if (request.categoryId() == null) {
            throw new BusinessRuleException("La categoría es obligatoria");
        }
        validateAmount(request.amount());
        if (request.expenseDate() == null) {
            throw new BusinessRuleException("La fecha del gasto es obligatoria");
        }
        if (request.expenseDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("La fecha del gasto no puede ser futura");
        }

        requireActiveArsOrganization(request.organizationId());

        // Validar existencia y estado del usuario creador
        var creator = userRepository.findById(request.createdByUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario creador no encontrado con id: " + request.createdByUserId()));
        if (!Boolean.TRUE.equals(creator.getIsActive())) {
            throw new BusinessRuleException("El usuario creador está inactivo");
        }

        // Validar miembro pagador
        UserOrganization payerMembership = userOrganizationRepository.findById(request.paidByMembershipId())
                .orElseThrow(() -> new ResourceNotFoundException("Membresía del pagador no encontrada con id: " + request.paidByMembershipId()));
        requireActiveMembership(payerMembership, request.organizationId(), "El miembro pagador");

        // Validar membresía del usuario creador en la organización
        UserOrganization creatorMembership = userOrganizationRepository.findByUserIdAndOrganizationId(request.createdByUserId(), request.organizationId())
                .orElseThrow(() -> new BusinessRuleException("El usuario que registra no pertenece a la organización"));
        requireActiveMembership(creatorMembership, request.organizationId(), "El usuario creador");

        // Regla: USER solo puede cargar gastos a su propio nombre. ADMIN puede cargar en su nombre o en nombre de otro usuario
        if (creatorMembership.getRole() == MembershipRole.USER) {
            if (!payerMembership.getUserId().equals(request.createdByUserId())) {
                throw new BusinessRuleException("Un usuario regular solo puede cargar gastos a su propio nombre");
            }
        }

        // Validar categoría perteneciente a la organización
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id: " + request.categoryId()));
        if (!category.getOrganizationId().equals(request.organizationId())) {
            throw new BusinessRuleException("La categoría no pertenece a la organización del gasto");
        }
        if (!Boolean.TRUE.equals(category.getIsActive())) {
            throw new BusinessRuleException("No se puede cargar un gasto en una categoría inactiva");
        }

        // Calcular los splits según SplitMode
        List<ExpenseSplit> splits = calculateSplits(request, payerMembership);

        // Guardar gasto
        BigDecimal normalizedAmount = validateAmount(request.amount());
        Expense expense = new Expense(
                null,
                request.organizationId(),
                request.createdByUserId(),
                request.paidByMembershipId(),
                request.categoryId(),
                normalizedAmount,
                request.expenseDate(),
                request.description(),
                request.receiptUrl(),
                LocalDateTime.now(),
                true
        );
        expense.setSplitType(request.splitMode() == null || request.splitMode() == SplitMode.INDIVIDUAL
                ? "INDIVIDUAL"
                : request.splitMode() == SplitMode.CUSTOM ? "CUSTOM" : "EQUAL");

        Expense savedExpense = expenseRepository.save(expense);

        // Asociar ID del gasto a cada split y guardar
        for (ExpenseSplit split : splits) {
            split.setExpenseId(savedExpense.getId());
        }
        List<ExpenseSplit> savedSplits = expenseSplitRepository.saveAll(splits);

        return new ExpenseDetailResponse(savedExpense, savedSplits);
    }

    private List<ExpenseSplit> calculateSplits(ExpenseCreateRequest request, UserOrganization payerMembership) {
        BigDecimal totalAmount = validateAmount(request.amount());
        SplitMode mode = request.splitMode() != null ? request.splitMode() : SplitMode.INDIVIDUAL;
        List<ExpenseSplit> splits = new ArrayList<>();

        switch (mode) {
            case INDIVIDUAL -> {
                // 100% al miembro pagador
                splits.add(new ExpenseSplit(null, null, payerMembership.getId(), totalAmount));
            }
            case BETWEEN_TWO, SPECIFIC_GROUP -> {
                List<Long> memberIds = new ArrayList<>();
                if (request.splitMemberIds() != null) {
                    memberIds.addAll(request.splitMemberIds());
                }
                if (!memberIds.contains(payerMembership.getId())) {
                    memberIds.add(payerMembership.getId());
                }

                if (request.splitMemberIds() == null || request.splitMemberIds().isEmpty()
                        || (mode == SplitMode.BETWEEN_TWO && new HashSet<>(memberIds).size() != 2)) {
                    throw new BusinessRuleException("Debe seleccionar participantes válidos para la modalidad de reparto");
                }
                validateAndDivideEqually(request.organizationId(), memberIds, totalAmount, splits);
            }
            case ALL_MEMBERS -> {
                List<UserOrganization> orgMembers = userOrganizationRepository.findByOrganizationId(request.organizationId())
                        .stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                        .filter(m -> userRepository.findById(m.getUserId())
                                .filter(user -> Boolean.TRUE.equals(user.getIsActive())).isPresent())
                        .toList();

                if (orgMembers.isEmpty()) {
                    throw new BusinessRuleException("No hay miembros activos en la organización para repartir el gasto");
                }
                List<Long> memberIds = orgMembers.stream().map(UserOrganization::getId).toList();
                validateAndDivideEqually(request.organizationId(), memberIds, totalAmount, splits);
            }
            case CUSTOM -> {
                if (request.customSplits() == null || request.customSplits().isEmpty()) {
                    throw new BusinessRuleException("Debe proveer la lista de splits personalizados");
                }
                Set<Long> seenMembers = new HashSet<>();
                BigDecimal sum = BigDecimal.ZERO;

                for (ExpenseSplitRequest splitReq : request.customSplits()) {
                    if (splitReq == null || splitReq.userOrganizationId() == null) {
                        throw new BusinessRuleException("El ID de membresía en el reparto no puede ser nulo");
                    }
                    if (seenMembers.contains(splitReq.userOrganizationId())) {
                        throw new BusinessRuleException("Cada miembro puede figurar a lo sumo una vez en la división de un mismo gasto");
                    }
                    seenMembers.add(splitReq.userOrganizationId());

                    UserOrganization member = userOrganizationRepository.findById(splitReq.userOrganizationId())
                            .orElseThrow(() -> new ResourceNotFoundException("Miembro en reparto no encontrado: " + splitReq.userOrganizationId()));
                    requireActiveMembership(member, request.organizationId(),
                            "El miembro " + splitReq.userOrganizationId());

                    if (splitReq.amount() == null || splitReq.amount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new BusinessRuleException("Cada parte asignada debe ser mayor a cero");
                    }

                    BigDecimal partAmount = validateAmount(splitReq.amount());
                    sum = sum.add(partAmount);
                    splits.add(new ExpenseSplit(null, null, member.getId(), partAmount));
                }

                if (sum.compareTo(totalAmount) != 0) {
                    throw new BusinessRuleException("La suma de las partes (" + sum + ") debe coincidir exactamente con el total del gasto (" + totalAmount + ")");
                }
            }
        }

        return splits;
    }

    private void validateAndDivideEqually(Long orgId, List<Long> memberIds, BigDecimal totalAmount, List<ExpenseSplit> splits) {
        if (memberIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(memberIds).size() != memberIds.size()) {
            throw new BusinessRuleException("Los participantes no pueden ser nulos ni repetidos");
        }
        Set<Long> uniqueMemberIds = new HashSet<>(memberIds);
        if (uniqueMemberIds.isEmpty()) {
            throw new BusinessRuleException("Debe seleccionar al menos un participante para la división del gasto");
        }

        for (Long memberId : uniqueMemberIds) {
            UserOrganization member = userOrganizationRepository.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("Miembro no encontrado: " + memberId));
            requireActiveMembership(member, orgId, "El miembro con ID " + memberId);
        }

        for (ExpenseSplit part : equalParts(totalAmount, memberIds)) {
            splits.add(part);
        }
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("El importe debe ser mayor a cero");
        }
        if (amount.scale() > 2) {
            throw new BusinessRuleException("El importe admite como máximo dos decimales");
        }
        if (amount.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new BusinessRuleException("El importe excede el límite máximo permitido");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private List<ExpenseSplit> equalParts(BigDecimal amount, List<Long> memberIds) {
        List<Long> sorted = memberIds.stream().sorted().toList();
        long cents = amount.movePointRight(2).longValueExact();
        if (sorted.isEmpty() || cents < sorted.size()) {
            throw new BusinessRuleException("El importe debe permitir al menos un centavo por participante");
        }
        List<ExpenseSplit> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            result.add(new ExpenseSplit(null, null, sorted.get(i),
                    BigDecimal.valueOf(cents / sorted.size() + (i < cents % sorted.size() ? 1 : 0), 2)));
        }
        return result;
    }

    private void recalculateParts(Expense expense, List<ExpenseSplit> parts, BigDecimal amount) {
        if (parts.isEmpty() || parts.stream().anyMatch(p -> p.getUserOrganizationId() == null)
                || parts.stream().map(ExpenseSplit::getUserOrganizationId).distinct().count() != parts.size()) {
            throw new BusinessRuleException("El reparto histórico está incompleto o contiene participantes duplicados");
        }
        parts.sort(Comparator.comparing(ExpenseSplit::getUserOrganizationId));
        if (!"CUSTOM".equals(expense.getSplitType())) {
            List<ExpenseSplit> calculated = equalParts(amount,
                    parts.stream().map(ExpenseSplit::getUserOrganizationId).toList());
            for (int i = 0; i < parts.size(); i++) parts.get(i).setAmount(calculated.get(i).getAmount());
            return;
        }
        BigDecimal previousSum = parts.stream().map(ExpenseSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (previousSum.compareTo(expense.getAmount()) != 0
                || parts.stream().anyMatch(p -> p.getAmount().signum() <= 0)) {
            throw new BusinessRuleException("El reparto histórico no coincide con el total del gasto");
        }
        BigInteger total = amount.movePointRight(2).toBigIntegerExact();
        BigInteger previous = previousSum.movePointRight(2).toBigIntegerExact();
        Map<Long, BigInteger> remainders = new HashMap<>();
        BigInteger allocated = BigInteger.ZERO;
        for (ExpenseSplit part : parts) {
            BigInteger[] division = total.multiply(part.getAmount().movePointRight(2).toBigIntegerExact())
                    .divideAndRemainder(previous);
            part.setAmount(new BigDecimal(division[0], 2));
            remainders.put(part.getUserOrganizationId(), division[1]);
            allocated = allocated.add(division[0]);
        }
        List<ExpenseSplit> byRemainder = parts.stream().sorted(
                Comparator.<ExpenseSplit, BigInteger>comparing(p -> remainders.get(p.getUserOrganizationId()))
                        .reversed().thenComparing(ExpenseSplit::getUserOrganizationId)).toList();
        int remaining = total.subtract(allocated).intValueExact();
        for (int i = 0; i < remaining; i++) {
            ExpenseSplit part = byRemainder.get(i);
            part.setAmount(part.getAmount().add(new BigDecimal("0.01")));
        }
        if (parts.stream().anyMatch(p -> p.getAmount().signum() <= 0)) {
            throw new BusinessRuleException("El nuevo importe deja participantes sin al menos un centavo");
        }
    }

    @Override
    public ExpenseDetailResponse getExpenseById(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con id: " + id));
        requireActiveArsOrganization(expense.getOrganizationId());
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(id);
        return new ExpenseDetailResponse(expense, splits);
    }

    @Override
    public List<ExpenseDetailResponse> getExpensesByOrganization(Long organizationId) {
        requireActiveArsOrganization(organizationId);
        List<Expense> expenses = expenseRepository.findByOrganizationId(organizationId);
        return expenses.stream()
                .map(e -> new ExpenseDetailResponse(e, expenseSplitRepository.findByExpenseId(e.getId())))
                .toList();
    }

    @Override
    @Transactional
    public ExpenseDetailResponse updateExpense(Long id, ExpenseUpdateRequest request) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con id: " + id));
        requireActiveArsOrganization(expense.getOrganizationId());
        if (!Boolean.TRUE.equals(expense.getIsActive())) {
            throw new BusinessRuleException("El gasto está anulado y no se puede modificar");
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id: " + request.categoryId()));
            if (!category.getOrganizationId().equals(expense.getOrganizationId())) {
                throw new BusinessRuleException("La categoría debe pertenecer a la organización del gasto");
            }
            if (!Boolean.TRUE.equals(category.getIsActive())
                    && !category.getId().equals(expense.getCategoryId())) {
                throw new BusinessRuleException("No se puede cambiar a una categoría inactiva");
            }
            expense.setCategoryId(request.categoryId());
        }

        if (request.expenseDate() != null) {
            if (request.expenseDate().isAfter(LocalDate.now())) {
                throw new BusinessRuleException("La fecha del gasto no puede ser futura");
            }
            expense.setExpenseDate(request.expenseDate());
        }

        if (request.description() != null) {
            expense.setDescription(request.description());
        }

        if (request.receiptUrl() != null) {
            expense.setReceiptUrl(request.receiptUrl());
        }

        if (request.amount() != null) {
            BigDecimal newAmount = validateAmount(request.amount());
            List<ExpenseSplit> existingSplits = new ArrayList<>(expenseSplitRepository.findByExpenseId(id));
            recalculateParts(expense, existingSplits, newAmount);
            expense.setAmount(newAmount);
            for (ExpenseSplit split : existingSplits) expenseSplitRepository.save(split);
        }

        Expense updated = expenseRepository.save(expense);
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(id);
        return new ExpenseDetailResponse(updated, splits);
    }

    @Override
    public Expense deactivateExpense(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con id: " + id));
        requireActiveArsOrganization(expense.getOrganizationId());
        if (!Boolean.TRUE.equals(expense.getIsActive())) {
            throw new BusinessRuleException("El gasto ya está anulado");
        }
        expense.setIsActive(false);
        return expenseRepository.save(expense);
    }

    @Override
    public com.shareverance.dto.contract.HistoryContractResponse getMonthlyHistoryContract(Long organizationId, String targetMonth) {
        Organization organization = requireActiveArsOrganization(organizationId);

        // 1. Obtener gastos activos de la organización
        List<Expense> activeExpenses = expenseRepository.findByOrganizationId(organizationId)
                .stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .toList();

        // 2. Determinar mes en curso (se excluye por ser mes incompleto)
        java.time.YearMonth currentYearMonth = java.time.YearMonth.now();

        // 3. Filtrar gastos correspondientes a meses cerrados completos (< currentYearMonth)
        List<Expense> closedMonthsExpenses = activeExpenses.stream()
                .filter(e -> java.time.YearMonth.from(e.getExpenseDate()).isBefore(currentYearMonth))
                .toList();

        // Si no hay historial cerrado
        if (closedMonthsExpenses.isEmpty()) {
            String nextMonth = (targetMonth != null && !targetMonth.isBlank())
                    ? targetMonth
                    : currentYearMonth.plusMonths(1).toString();

            return new com.shareverance.dto.contract.HistoryContractResponse(
                    "1",
                    UUID.randomUUID().toString(),
                    organizationId,
                    "ARS",
                    nextMonth,
                    List.of(),
                    List.of()
            );
        }

        // 4. Encontrar rango de meses (mínimo y máximo)
        java.time.YearMonth minMonth = closedMonthsExpenses.stream()
                .map(e -> java.time.YearMonth.from(e.getExpenseDate()))
                .min(Comparator.naturalOrder())
                .orElse(currentYearMonth.minusMonths(1));

        java.time.YearMonth maxMonth = closedMonthsExpenses.stream()
                .map(e -> java.time.YearMonth.from(e.getExpenseDate()))
                .max(Comparator.naturalOrder())
                .orElse(currentYearMonth.minusMonths(1));

        // 5. Categorías con gastos en el historial
        Set<Long> categoryIdsInHistory = closedMonthsExpenses.stream()
                .map(Expense::getCategoryId)
                .collect(Collectors.toSet());

        // Ordenar categorías por ID
        List<Long> sortedCategoryIds = categoryIdsInHistory.stream().sorted().toList();

        // available_from_month para cada categoría
        List<com.shareverance.dto.contract.CategoryCoverageDto> categoriesCoverage = new ArrayList<>();
        Map<Long, java.time.YearMonth> categoryAvailableFrom = new HashMap<>();

        for (Long catId : sortedCategoryIds) {
            java.time.YearMonth firstMonth = closedMonthsExpenses.stream()
                    .filter(e -> e.getCategoryId().equals(catId))
                    .map(e -> java.time.YearMonth.from(e.getExpenseDate()))
                    .min(Comparator.naturalOrder())
                    .orElse(minMonth);

            categoryAvailableFrom.put(catId, firstMonth);
            categoriesCoverage.add(new com.shareverance.dto.contract.CategoryCoverageDto(catId, firstMonth.toString()));
        }

        // 6. Construir meses consecutivos desde minMonth hasta maxMonth
        List<com.shareverance.dto.contract.MonthlyHistoryDto> historyList = new ArrayList<>();
        java.time.YearMonth iterMonth = minMonth;

        while (!iterMonth.isAfter(maxMonth)) {
            final java.time.YearMonth m = iterMonth;
            List<com.shareverance.dto.contract.CategoryTotalDto> categoryTotals = new ArrayList<>();

            for (Long catId : sortedCategoryIds) {
                java.time.YearMonth availableFrom = categoryAvailableFrom.get(catId);
                // Si la categoría está disponible en este mes (m >= availableFrom)
                if (!m.isBefore(availableFrom)) {
                    // Sumar gastos de esta categoría en este mes
                    BigDecimal sum = closedMonthsExpenses.stream()
                            .filter(e -> e.getCategoryId().equals(catId) && java.time.YearMonth.from(e.getExpenseDate()).equals(m))
                            .map(Expense::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    long amountMinor = sum.multiply(BigDecimal.valueOf(100)).longValue();
                    categoryTotals.add(new com.shareverance.dto.contract.CategoryTotalDto(catId, amountMinor));
                }
            }

            historyList.add(new com.shareverance.dto.contract.MonthlyHistoryDto(m.toString(), categoryTotals));
            iterMonth = iterMonth.plusMonths(1);
        }

        // Limitar a los últimos 60 meses si excediera
        if (historyList.size() > 60) {
            historyList = historyList.subList(historyList.size() - 60, historyList.size());
        }

        // 7. Determinar target_month: mes inmediatamente posterior al último mes del historial
        String resolvedTargetMonth = (targetMonth != null && !targetMonth.isBlank())
                ? targetMonth
                : maxMonth.plusMonths(1).toString();

        return new com.shareverance.dto.contract.HistoryContractResponse(
                "1",
                UUID.randomUUID().toString(),
                organizationId,
                "ARS",
                resolvedTargetMonth,
                categoriesCoverage,
                historyList
        );
    }

    private Organization requireActiveArsOrganization(Long organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organización no encontrada con id: " + organizationId));
        if (!Boolean.TRUE.equals(organization.getIsActive())) {
            throw new BusinessRuleException("La organización está inactiva");
        }
        if (!"ARS".equalsIgnoreCase(organization.getCurrency())) {
            throw new BusinessRuleException("La única moneda admitida es ARS");
        }
        return organization;
    }

    private void requireActiveMembership(UserOrganization membership, Long organizationId, String subject) {
        if (!organizationId.equals(membership.getOrganizationId())) {
            throw new BusinessRuleException(subject + " no pertenece a la organización del gasto");
        }
        if (!Boolean.TRUE.equals(membership.getIsActive())) {
            throw new BusinessRuleException(subject + " tiene una membresía inactiva");
        }
        boolean userIsActive = userRepository.findById(membership.getUserId())
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .isPresent();
        if (!userIsActive) {
            throw new BusinessRuleException(subject + " tiene una cuenta inactiva");
        }
    }
}
