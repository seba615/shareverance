CREATE TABLE receipts (
    id VARCHAR(36) PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    expense_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_receipt_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id),

    CONSTRAINT fk_receipt_user
        FOREIGN KEY (uploaded_by_user_id)
        REFERENCES users(id),

    CONSTRAINT fk_receipt_expense
        FOREIGN KEY (expense_id)
        REFERENCES expenses(id)
);

CREATE INDEX idx_receipts_organization_id ON receipts(organization_id);
CREATE INDEX idx_receipts_expense_id ON receipts(expense_id);
CREATE INDEX idx_receipts_uploaded_by_user_id ON receipts(uploaded_by_user_id);

DO $$
DECLARE
    business_schema TEXT := current_schema();
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'shareverance_java') THEN
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE %I.receipts TO shareverance_java', business_schema);
    END IF;
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'shareverance_python') THEN
        EXECUTE format('REVOKE ALL ON TABLE %I.receipts FROM shareverance_python', business_schema);
    END IF;
END $$;
