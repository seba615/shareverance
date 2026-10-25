package com.shareverance.service;

import com.shareverance.dto.IndividualSummaryResponse;
import com.shareverance.repository.UserOrganizationRepository;
import com.shareverance.repository.jdbc.JdbcIndividualSummaryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class IndividualSummaryService {
    private final AuthService auth;
    private final UserOrganizationRepository memberships;
    private final JdbcIndividualSummaryRepository repository;
    public IndividualSummaryService(AuthService auth, UserOrganizationRepository memberships,
            JdbcIndividualSummaryRepository repository) {
        this.auth = auth; this.memberships = memberships; this.repository = repository;
    }

    @Transactional(readOnly = true)
    public IndividualSummaryResponse summarize(Long userId, Long organizationId, LocalDate from, LocalDate to) {
        return summarize(userId, organizationId, from, to, null);
    }

    @Transactional(readOnly = true)
    public IndividualSummaryResponse summarize(Long userId, Long organizationId, LocalDate from,
            LocalDate to, Long requestedMembershipId) {
        if (!auth.isOrganizationMember(userId, organizationId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        if ((from != null && (from.getYear() < 1 || from.getYear() > 9999))
                || (to != null && (to.getYear() < 1 || to.getYear() > 9999))
                || (from != null && to != null && from.isAfter(to)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El período de consulta no es válido");
        var membership = memberships.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Membresía no disponible"));
        if (requestedMembershipId != null && !requestedMembershipId.equals(membership.getId())) {
            if (!auth.isOrganizationAdmin(userId, organizationId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sólo ADMIN puede consultar otro miembro");
            membership = memberships.findById(requestedMembershipId)
                    .filter(m -> organizationId.equals(m.getOrganizationId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Miembro ajeno o no disponible"));
        }
        var categories = repository.summarize(organizationId, membership.getId(), from, to);
        var total = categories.stream().map(c -> c.amount()).reduce(new BigDecimal("0.00"), BigDecimal::add);
        long count = categories.stream().mapToLong(c -> c.expenseCount()).sum();
        return new IndividualSummaryResponse(organizationId, membership.getId(), "ARS", from, to, total, count, categories);
    }
}
