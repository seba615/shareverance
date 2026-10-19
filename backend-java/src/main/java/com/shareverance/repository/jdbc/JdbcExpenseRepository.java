package com.shareverance.repository.jdbc;

import com.shareverance.model.Expense;
import com.shareverance.repository.ExpenseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcExpenseRepository implements ExpenseRepository {

    private static final RowMapper<Expense> ROW_MAPPER = (rs, rowNum) -> {
        Expense expense = new Expense(
                rs.getLong("id"),
                rs.getLong("organization_id"),
                rs.getLong("created_by_user_id"),
                rs.getLong("paid_by_membership_id"),
                rs.getLong("category_id"),
                rs.getBigDecimal("amount"),
                rs.getDate("expense_date").toLocalDate(),
                rs.getString("description"),
                rs.getString("receipt_url"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getBoolean("is_active"),
                rs.getInt("version")
        );
        expense.setSplitType(rs.getString("split_type"));
        return expense;
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcExpenseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Expense save(Expense expense) {
        if (expense.getId() == null) {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO expenses (
                        organization_id, created_by_user_id, paid_by_membership_id, category_id,
                        amount, expense_date, description, receipt_url, split_type, created_at, is_active, version
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, TRUE), COALESCE(?, 1))
                    RETURNING id, organization_id, created_by_user_id, paid_by_membership_id, category_id,
                              amount, expense_date, description, receipt_url, split_type, created_at, is_active, version
                    """, ROW_MAPPER,
                    expense.getOrganizationId(), expense.getCreatedByUserId(), expense.getPaidByMembershipId(),
                    expense.getCategoryId(), expense.getAmount(), Date.valueOf(expense.getExpenseDate()),
                    expense.getDescription(), expense.getReceiptUrl(), expense.getSplitType(),
                    timestamp(expense.getCreatedAt()), expense.getIsActive(), expense.getVersion());
        }

        return jdbcTemplate.queryForObject("""
                UPDATE expenses
                SET organization_id = ?, created_by_user_id = ?, paid_by_membership_id = ?, category_id = ?,
                    amount = ?, expense_date = ?, description = ?, receipt_url = ?, split_type = ?,
                    created_at = ?, is_active = ?, version = version + 1
                WHERE id = ?
                RETURNING id, organization_id, created_by_user_id, paid_by_membership_id, category_id,
                          amount, expense_date, description, receipt_url, split_type, created_at, is_active, version
                """, ROW_MAPPER,
                expense.getOrganizationId(), expense.getCreatedByUserId(), expense.getPaidByMembershipId(),
                expense.getCategoryId(), expense.getAmount(), Date.valueOf(expense.getExpenseDate()),
                expense.getDescription(), expense.getReceiptUrl(), expense.getSplitType(),
                timestamp(expense.getCreatedAt()), expense.getIsActive(), expense.getId());
    }

    @Override
    public Optional<Expense> updateWithOptimisticLocking(Expense expense, Integer expectedVersion) {
        List<Expense> updated = jdbcTemplate.query("""
                UPDATE expenses
                SET organization_id = ?, created_by_user_id = ?, paid_by_membership_id = ?, category_id = ?,
                    amount = ?, expense_date = ?, description = ?, receipt_url = ?, split_type = ?,
                    created_at = ?, is_active = ?, version = version + 1
                WHERE id = ? AND version = ?
                RETURNING id, organization_id, created_by_user_id, paid_by_membership_id, category_id,
                          amount, expense_date, description, receipt_url, split_type, created_at, is_active, version
                """, ROW_MAPPER,
                expense.getOrganizationId(), expense.getCreatedByUserId(), expense.getPaidByMembershipId(),
                expense.getCategoryId(), expense.getAmount(), Date.valueOf(expense.getExpenseDate()),
                expense.getDescription(), expense.getReceiptUrl(), expense.getSplitType(),
                timestamp(expense.getCreatedAt()), expense.getIsActive(),
                expense.getId(), expectedVersion);
        return updated.stream().findFirst();
    }

    @Override
    public Optional<Expense> findById(Long id) {
        return jdbcTemplate.query("""
                SELECT id, organization_id, created_by_user_id, paid_by_membership_id, category_id,
                       amount, expense_date, description, receipt_url, split_type, created_at, is_active, version
                FROM expenses
                WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<Expense> findByOrganizationId(Long organizationId) {
        return jdbcTemplate.query("""
                SELECT id, organization_id, created_by_user_id, paid_by_membership_id, category_id,
                       amount, expense_date, description, receipt_url, split_type, created_at, is_active, version
                FROM expenses
                WHERE organization_id = ?
                ORDER BY id
                """, ROW_MAPPER, organizationId);
    }

    @Override
    public boolean existsById(Long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM expenses WHERE id = ?)", Boolean.class, id));
    }

    private static Timestamp timestamp(java.time.LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
