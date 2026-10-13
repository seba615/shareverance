package com.shareverance.repository.jdbc;

import com.shareverance.model.ExpenseSplit;
import com.shareverance.repository.ExpenseSplitRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class JdbcExpenseSplitRepository implements ExpenseSplitRepository {

    private static final RowMapper<ExpenseSplit> ROW_MAPPER = (rs, rowNum) -> new ExpenseSplit(
            rs.getLong("id"),
            rs.getLong("expense_id"),
            rs.getLong("user_organization_id"),
            rs.getBigDecimal("amount")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcExpenseSplitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ExpenseSplit save(ExpenseSplit split) {
        if (split.getId() == null) {
            return jdbcTemplate.queryForObject("""
                    INSERT INTO expense_splits (organization_id, expense_id, user_organization_id, amount)
                    SELECT organization_id, ?, ?, ?
                    FROM expenses
                    WHERE id = ?
                    RETURNING id, expense_id, user_organization_id, amount
                    """, ROW_MAPPER,
                    split.getExpenseId(), split.getUserOrganizationId(), split.getAmount(), split.getExpenseId());
        }

        return jdbcTemplate.queryForObject("""
                UPDATE expense_splits
                SET user_organization_id = ?, amount = ?
                WHERE id = ?
                RETURNING id, expense_id, user_organization_id, amount
                """, ROW_MAPPER,
                split.getUserOrganizationId(), split.getAmount(), split.getId());
    }

    @Override
    @Transactional
    public List<ExpenseSplit> saveAll(List<ExpenseSplit> splits) {
        return splits.stream().map(this::save).toList();
    }

    @Override
    public List<ExpenseSplit> findByExpenseId(Long expenseId) {
        return jdbcTemplate.query("""
                SELECT id, expense_id, user_organization_id, amount
                FROM expense_splits
                WHERE expense_id = ?
                ORDER BY id
                """, ROW_MAPPER, expenseId);
    }

    @Override
    public void deleteByExpenseId(Long expenseId) {
        jdbcTemplate.update("DELETE FROM expense_splits WHERE expense_id = ?", expenseId);
    }
}
