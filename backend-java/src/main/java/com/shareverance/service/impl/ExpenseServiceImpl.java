package com.shareverance.service.impl;

import java.math.BigDecimal;
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
import com.shareverance.service.ReceiptService;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final CategoryRepository categoryRepository;
    private final ReceiptService receiptService;

    @Autowired
    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                              ExpenseSplitRepository expenseSplitRepository,
                              OrganizationRepository organizationRepository,
                              UserRepository userRepository,
                              UserOrganizationRepository userOrganizationRepository,
                              CategoryRepository categoryRepository,
                              @Autowired(required = false) ReceiptService receiptService) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.categoryRepository = categoryRepository;
        this.receiptService = receiptService;
    }

    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                              ExpenseSplitRepository expenseSplitRepository,
                              OrganizationRepository organizationRepository,
                              UserRepository userRepository,
                              UserOrganizationRepository userOrganizationRepository,
                              CategoryRepository categoryRepository) {
        this(expenseRepository, expenseSplitRepository, organizationRepository,
                userRepository, userOrganizationRepository, categoryRepository, null);
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
        if (request.expenseDate().isAfter(LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")))) {
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

        validateDescription(request.description());

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
                : "EQUAL");

        Expense savedExpense = expenseRepository.save(expense);

        // Asociar ID del gasto a cada split y guardar
        for (ExpenseSplit split : splits) {
            split.setExpenseId(savedExpense.getId());
        }
        List<ExpenseSplit> savedSplits = expenseSplitRepository.saveAll(splits);

        if (receiptService != null && request.receiptUrl() != null && !request.receiptUrl().isBlank()) {
            String candidateReceiptId = request.receiptUrl().trim();
            if (candidateReceiptId.contains("/")) {
                candidateReceiptId = candidateReceiptId.substring(candidateReceiptId.lastIndexOf('/') + 1);
            }
            try {
                receiptService.linkReceiptToExpense(
                        candidateReceiptId,
                        savedExpense.getId(),
                        request.organizationId(),
                        request.createdByUserId()
                );
            } catch (Exception ignored) {
                // Si la cadena receiptUrl no es un ID de comprobante registrado, se conserva el texto sin vincular entidad
            }
        }

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
                    if (splitReq.userOrganizationId() == null) {
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

                    BigDecimal partAmount = splitReq.amount().setScale(2, RoundingMode.HALF_UP);
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

        splits.addAll(equalParts(totalAmount, memberIds));
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank() || description.length() > 500)
            throw new BusinessRuleException("La descripción es obligatoria y admite hasta 500 caracteres");
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
        if (memberIds.isEmpty() || memberIds.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(memberIds).size() != memberIds.size()) {
            throw new BusinessRuleException("El reparto debe contener participantes únicos y no nulos");
        }
        List<Long> sorted = memberIds.stream().sorted().toList();
        long cents = amount.movePointRight(2).longValueExact();
        if (cents < sorted.size()) {
            throw new BusinessRuleException("El importe debe permitir al menos un centavo por participante");
        }
        List<ExpenseSplit> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            result.add(new ExpenseSplit(null, null, sorted.get(i),
                    BigDecimal.valueOf(cents / sorted.size() + (i < cents % sorted.size() ? 1 : 0), 2)));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public com.shareverance.dto.ExpensePageResponse getExpensePage(Long organizationId,
            com.shareverance.dto.ExpenseFilter filter, int page, int size) {
        requireActiveArsOrganization(organizationId);
        if (page < 0 || size < 1 || size > 100) throw new BusinessRuleException("Página o tamaño inválidos (1 a 100)");
        if (!List.of("ACTIVE", "ANNULLED", "ALL").contains(filter.state())) throw new BusinessRuleException("Estado inválido");
        if (filter.fromDate() != null && filter.toDate() != null && filter.fromDate().isAfter(filter.toDate()))
            throw new BusinessRuleException("El inicio del período no puede ser posterior al fin");
        if (filter.categoryId() != null) {
            Category category = categoryRepository.findById(filter.categoryId())
                    .orElseThrow(() -> new BusinessRuleException("Categoría no válida para el filtro"));
            if (!organizationId.equals(category.getOrganizationId())) throw new BusinessRuleException("Categoría de otra organización");
        }
        var result = expenseRepository.findPage(organizationId, filter, page, size);
        var items = result.expenses().stream().map(e -> new ExpenseDetailResponse(e,
                expenseSplitRepository.findByExpenseId(e.getId()))).toList();
        return new com.shareverance.dto.ExpensePageResponse(items, page, size, result.totalElements(),
                (result.totalElements() + size - 1) / size, result.totalAmount());
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public com.shareverance.dto.ExpenseCalendarResponse getExpenseCalendar(Long organizationId, String month) {
        requireActiveArsOrganization(organizationId);
        java.time.YearMonth currentMonth = java.time.YearMonth.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        java.time.YearMonth targetMonth;
        if (month == null || month.isBlank()) {
            targetMonth = currentMonth;
        } else {
            try {
                targetMonth = java.time.YearMonth.parse(month.trim());
            } catch (java.time.format.DateTimeParseException ex) {
                throw new BusinessRuleException("Formato de mes inválido (use YYYY-MM)");
            }
        }
        if (targetMonth.isAfter(currentMonth)) {
            throw new BusinessRuleException("No se pueden consultar meses futuros en el calendario");
        }
        var days = expenseRepository.findCalendarDays(
                organizationId, targetMonth.atDay(1), targetMonth.atEndOfMonth());
        BigDecimal totalAmount = days.stream()
                .map(com.shareverance.dto.ExpenseCalendarDayResponse::totalAmount)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        long totalExpenses = days.stream()
                .mapToLong(com.shareverance.dto.ExpenseCalendarDayResponse::expenseCount)
                .sum();
        return new com.shareverance.dto.ExpenseCalendarResponse(
                targetMonth.toString(), totalAmount, totalExpenses, days);
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
        if (request.version() == null || request.version() <= 0) {
            throw new BusinessRuleException("La versión es obligatoria y debe ser positiva");
        }
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con id: " + id));
        requireActiveArsOrganization(expense.getOrganizationId());
        if (!request.version().equals(expense.getVersion())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Conflicto de concurrencia: el gasto fue modificado por otra transacción");
        }
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
            if (request.expenseDate().isAfter(LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")))) {
                throw new BusinessRuleException("La fecha del gasto no puede ser futura");
            }
            expense.setExpenseDate(request.expenseDate());
        }

        if (request.description() != null) {
            validateDescription(request.description());
            expense.setDescription(request.description());
        }

        if (request.receiptUrl() != null) {
            expense.setReceiptUrl(request.receiptUrl());
        }

        List<ExpenseSplit> partsToSave = List.of();
        if (request.amount() != null) {
            BigDecimal newAmount = validateAmount(request.amount());
            List<ExpenseSplit> existingSplits = expenseSplitRepository.findByExpenseId(id);
            List<ExpenseSplit> calculated = equalParts(newAmount,
                    existingSplits.stream().map(ExpenseSplit::getUserOrganizationId).toList());
            Map<Long, BigDecimal> amounts = calculated.stream().collect(Collectors.toMap(
                    ExpenseSplit::getUserOrganizationId, ExpenseSplit::getAmount));
            expense.setAmount(newAmount);
            for (ExpenseSplit split : existingSplits) {
                split.setAmount(amounts.get(split.getUserOrganizationId()));
            }
            partsToSave = existingSplits;
        }

        // Primero asegurar versión y bloqueo del gasto; cualquier fallo posterior revierte todo.
        Expense updated = expenseRepository.updateWithOptimisticLocking(expense, request.version())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Conflicto de concurrencia: el gasto fue modificado por otra transacción"));
        for (ExpenseSplit split : partsToSave) expenseSplitRepository.save(split);
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(id);
        return new ExpenseDetailResponse(updated, splits);
    }

    @Override
    @Transactional
    public Expense deactivateExpense(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado con id: " + id));
        requireActiveArsOrganization(expense.getOrganizationId());
        if (!Boolean.TRUE.equals(expense.getIsActive())) {
            throw new BusinessRuleException("El gasto ya está anulado");
        }
        return expenseRepository.deactivateById(id)
                .orElseThrow(() -> new BusinessRuleException("El gasto ya está anulado"));
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
