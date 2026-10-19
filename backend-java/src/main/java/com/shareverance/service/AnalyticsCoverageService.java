package com.shareverance.service;

import com.shareverance.exception.BusinessRuleException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Date;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class AnalyticsCoverageService {
    public record Coverage(Long organizationId, String month, boolean confirmed) {}
    private final JdbcTemplate jdbc;
    private final AuthService auth;

    public AnalyticsCoverageService(JdbcTemplate jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    @Transactional(readOnly = true)
    public List<Coverage> list(Long organizationId, Long userId) {
        requireAdmin(organizationId, userId);
        return jdbc.query("SELECT covered_month, is_confirmed FROM analytics_month_coverage WHERE organization_id=? ORDER BY covered_month",
                (rs, row) -> new Coverage(organizationId, YearMonth.from(rs.getDate(1).toLocalDate()).toString(), rs.getBoolean(2)), organizationId);
    }

    @Transactional
    public Coverage declare(Long organizationId, Long userId, String month, boolean confirmed) {
        // Mismo orden que administración de miembros: organización antes de comprobar ADMIN.
        jdbc.query("SELECT id FROM organizations WHERE id=? FOR SHARE", (rs, row) -> rs.getLong(1), organizationId);
        requireAdmin(organizationId, userId);
        YearMonth parsed;
        try {
            if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new DateTimeParseException("", "", 0);
            parsed = YearMonth.parse(month);
            if (parsed.getYear() < 1 || !parsed.isBefore(YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires")))) {
                throw new BusinessRuleException("La cobertura requiere un mes completo anterior al actual");
            }
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException("Formato de mes inválido (YYYY-MM, años 0001 a 9999)");
        }
        jdbc.update("""
                INSERT INTO analytics_month_coverage(organization_id,covered_month,declared_by_user_id,is_confirmed)
                VALUES (?,?,?,?) ON CONFLICT (organization_id,covered_month) DO UPDATE
                SET declared_by_user_id=EXCLUDED.declared_by_user_id, declared_at=CURRENT_TIMESTAMP,
                    is_confirmed=EXCLUDED.is_confirmed
                """, organizationId, Date.valueOf(parsed.atDay(1)), userId, confirmed);
        return new Coverage(organizationId, month, confirmed);
    }

    private void requireAdmin(Long organizationId, Long userId) {
        if (userId == null || !auth.isOrganizationAdmin(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere ADMIN activo de la organización");
        }
    }
}
