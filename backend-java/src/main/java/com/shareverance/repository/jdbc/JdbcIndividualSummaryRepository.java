package com.shareverance.repository.jdbc;

import com.shareverance.dto.IndividualSummaryResponse.CategoryParticipation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcIndividualSummaryRepository {
    private final JdbcTemplate jdbc;
    public JdbcIndividualSummaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CategoryParticipation> summarize(Long organizationId, Long membershipId,
            LocalDate from, LocalDate to) {
        var sql = new StringBuilder("""
                SELECT c.id, c.name, c.is_active, SUM(s.amount) AS amount, COUNT(*) AS expense_count
                FROM expense_splits s
                JOIN expenses e ON e.id = s.expense_id AND e.organization_id = s.organization_id
                JOIN categories c ON c.id = e.category_id AND c.organization_id = e.organization_id
                JOIN user_organization m ON m.id = s.user_organization_id AND m.organization_id = e.organization_id
                WHERE e.organization_id = ? AND m.id = ? AND e.is_active = TRUE
                """);
        List<Object> args = new ArrayList<>(List.of(organizationId, membershipId));
        if (from != null) { sql.append(" AND e.expense_date >= ?"); args.add(Date.valueOf(from)); }
        if (to != null) { sql.append(" AND e.expense_date <= ?"); args.add(Date.valueOf(to)); }
        sql.append(" GROUP BY c.id, c.name, c.is_active ORDER BY c.name, c.id");
        return jdbc.query(sql.toString(), (rs, row) -> new CategoryParticipation(rs.getLong("id"),
                rs.getString("name"), rs.getBoolean("is_active"), rs.getBigDecimal("amount"),
                rs.getLong("expense_count")), args.toArray());
    }
}
