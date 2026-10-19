package com.shareverance.repository.jdbc;

import com.shareverance.model.Receipt;
import com.shareverance.repository.ReceiptRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcReceiptRepository implements ReceiptRepository {

    private static final RowMapper<Receipt> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("created_at");
        Long expenseId = rs.getObject("expense_id") != null ? rs.getLong("expense_id") : null;
        return new Receipt(
                rs.getString("id"),
                rs.getLong("organization_id"),
                rs.getLong("uploaded_by_user_id"),
                rs.getString("storage_path"),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getLong("file_size_bytes"),
                expenseId,
                ts != null ? ts.toInstant() : null,
                rs.getBoolean("is_active")
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcReceiptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Receipt save(Receipt receipt) {
        String sql = """
                INSERT INTO receipts (
                    id, organization_id, uploaded_by_user_id, storage_path,
                    original_filename, content_type, file_size_bytes, expense_id,
                    created_at, is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), COALESCE(?, TRUE))
                ON CONFLICT (id) DO UPDATE SET
                    storage_path = EXCLUDED.storage_path,
                    original_filename = EXCLUDED.original_filename,
                    content_type = EXCLUDED.content_type,
                    file_size_bytes = EXCLUDED.file_size_bytes,
                    expense_id = EXCLUDED.expense_id,
                    is_active = EXCLUDED.is_active
                RETURNING id, organization_id, uploaded_by_user_id, storage_path,
                          original_filename, content_type, file_size_bytes, expense_id,
                          created_at, is_active
                """;

        return jdbcTemplate.queryForObject(sql, ROW_MAPPER,
                receipt.getId(),
                receipt.getOrganizationId(),
                receipt.getUploadedByUserId(),
                receipt.getStoragePath(),
                receipt.getOriginalFilename(),
                receipt.getContentType(),
                receipt.getFileSizeBytes(),
                receipt.getExpenseId(),
                receipt.getCreatedAt() != null ? Timestamp.from(receipt.getCreatedAt()) : null,
                receipt.getIsActive()
        );
    }

    @Override
    public Optional<Receipt> findById(String id) {
        String sql = "SELECT * FROM receipts WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Receipt> findByIdAndOrganizationId(String id, Long organizationId) {
        String sql = "SELECT * FROM receipts WHERE id = ? AND organization_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, id, organizationId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Receipt> findByOrganizationId(Long organizationId) {
        String sql = "SELECT * FROM receipts WHERE organization_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, organizationId);
    }

    @Override
    public Optional<Receipt> findByExpenseId(Long expenseId) {
        String sql = "SELECT * FROM receipts WHERE expense_id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, expenseId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void updateExpenseId(String receiptId, Long expenseId) {
        String sql = "UPDATE receipts SET expense_id = ? WHERE id = ?";
        jdbcTemplate.update(sql, expenseId, receiptId);
    }

    @Override
    public void deactivateById(String id) {
        String sql = "UPDATE receipts SET is_active = FALSE WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}
