package com.shareverance;

import com.shareverance.model.Receipt;
import com.shareverance.repository.jdbc.JdbcReceiptRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "E12_TEST_JDBC_URL", matches = ".*shareverance_e12_equal_test.*")
class ReceiptsPostgresTest {

    private static JdbcTemplate jdbcTemplate;
    private static JdbcReceiptRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        String url = System.getenv("E12_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("E12_TEST_JDBC_USER", "e12");
        String password = System.getenv().getOrDefault("E12_TEST_JDBC_PASSWORD", "e12-local-test");

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                url + (url.contains("?") ? "&" : "?") + "currentSchema=e41_receipts", user, password
        );
        jdbcTemplate = new JdbcTemplate(dataSource);

        // Run migrations on isolated test schema
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("e41_receipts")
                .defaultSchema("e41_receipts")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        repository = new JdbcReceiptRepository(jdbcTemplate);

        // Seed basic org and user if not exists
        jdbcTemplate.update("""
                INSERT INTO organizations (id, name, type, currency, is_active)
                VALUES (1, 'Org Test 1', 'CONDO', 'ARS', TRUE)
                ON CONFLICT (id) DO NOTHING;
                """);
        jdbcTemplate.update("""
                INSERT INTO organizations (id, name, type, currency, is_active)
                VALUES (2, 'Org Test 2', 'HOUSEHOLD', 'ARS', TRUE)
                ON CONFLICT (id) DO NOTHING;
                """);
        jdbcTemplate.update("""
                INSERT INTO users (id, full_name, email, password_hash, is_active)
                VALUES (1, 'Usuario 1', 'u1@test.com', 'hash', TRUE)
                ON CONFLICT (id) DO NOTHING;
                """);
    }

    @Test
    void postgres_saveAndFindReceipt_success() {
        String receiptId = UUID.randomUUID().toString();
        Receipt receipt = new Receipt(
                receiptId, 1L, 1L, "org_1/" + receiptId + ".png",
                "comprobante.png", "image/png", 5120L, null, Instant.now(), true
        );

        Receipt saved = repository.save(receipt);
        assertNotNull(saved);
        assertEquals(receiptId, saved.getId());

        Optional<Receipt> found = repository.findByIdAndOrganizationId(receiptId, 1L);
        assertTrue(found.isPresent());
        assertEquals("comprobante.png", found.get().getOriginalFilename());
        assertEquals(5120L, found.get().getFileSizeBytes());

        // Isolation: Cannot find under different organization
        Optional<Receipt> notFoundWrongOrg = repository.findByIdAndOrganizationId(receiptId, 2L);
        assertFalse(notFoundWrongOrg.isPresent());
    }

    @Test
    void postgres_updateExpenseIdAndDeactivate() {
        String receiptId = UUID.randomUUID().toString();
        Receipt receipt = new Receipt(
                receiptId, 1L, 1L, "org_1/" + receiptId + ".jpg",
                "factura.jpg", "image/jpeg", 10240L, null, Instant.now(), true
        );
        repository.save(receipt);

        repository.updateExpenseId(receiptId, 999L);
        Receipt updated = repository.findById(receiptId).orElseThrow();
        assertEquals(999L, updated.getExpenseId());

        repository.deactivateById(receiptId);
        Receipt deactivated = repository.findById(receiptId).orElseThrow();
        assertFalse(deactivated.getIsActive());
    }

    @Test
    void postgres_findByOrganizationId_returnsOnlyOrgReceipts() {
        String r1 = UUID.randomUUID().toString();
        String r2 = UUID.randomUUID().toString();

        repository.save(new Receipt(r1, 1L, 1L, "org_1/" + r1 + ".png", "r1.png", "image/png", 100L, null, Instant.now(), true));
        repository.save(new Receipt(r2, 2L, 1L, "org_2/" + r2 + ".png", "r2.png", "image/png", 200L, null, Instant.now(), true));

        List<Receipt> org1List = repository.findByOrganizationId(1L);
        assertTrue(org1List.stream().anyMatch(r -> r.getId().equals(r1)));
        assertFalse(org1List.stream().anyMatch(r -> r.getId().equals(r2)));
    }
}
