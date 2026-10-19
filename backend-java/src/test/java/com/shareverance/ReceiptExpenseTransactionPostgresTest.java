package com.shareverance;

import com.shareverance.dto.*;
import com.shareverance.repository.jdbc.*;
import com.shareverance.service.*;
import com.shareverance.service.impl.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "E12_TEST_JDBC_URL", matches = ".*shareverance_e12_equal_test.*")
class ReceiptExpenseTransactionPostgresTest {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static ExpenseService service;

    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getenv("E12_TEST_JDBC_URL");
            return new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?")
                    + "currentSchema=e42_receipt_expenses", "e12", "e12-local-test");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean AuthService authService() {
            var auth = mock(AuthService.class);
            when(auth.isOrganizationMember(1L, 1L)).thenReturn(true);
            return auth;
        }
    }

    @BeforeAll static void prepare() {
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class, JdbcExpenseRepository.class, JdbcExpenseSplitRepository.class,
                JdbcOrganizationRepository.class, JdbcUserRepository.class, JdbcUserOrganizationRepository.class,
                JdbcCategoryRepository.class, JdbcReceiptRepository.class, ExpenseServiceImpl.class, ReceiptServiceImpl.class);
        context.refresh();
        Flyway.configure().dataSource(context.getBean(DataSource.class)).schemas("e42_receipt_expenses")
                .defaultSchema("e42_receipt_expenses").locations("classpath:db/migration").load().migrate();
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(ExpenseService.class);
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES(1,'Prueba','receipt1@example.invalid','test'),(2,'Prueba','receipt2@example.invalid','test')");
        jdbc.update("INSERT INTO organizations(id,name,type) VALUES(1,'A','HOUSEHOLD'),(2,'B','HOUSEHOLD')");
        jdbc.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES(1,1,1,'USER'),(2,2,1,'ADMIN'),(3,2,2,'ADMIN')");
        jdbc.update("INSERT INTO categories(id,organization_id,name) VALUES(1,1,'Prueba')");
    }
    @AfterAll static void close() { if (context != null) context.close(); }
    @BeforeEach void cleanFixture() {
        jdbc.execute("DROP TRIGGER IF EXISTS e42_receipt_failure ON receipts");
        jdbc.update("DELETE FROM receipts");
        jdbc.update("DELETE FROM expense_splits");
        jdbc.update("DELETE FROM expenses");
    }
    static ExpenseCreateRequest request(String receipt) {
        return new ExpenseCreateRequest(1L, 1L, 1L, 1L, new BigDecimal("10.01"),
                LocalDate.of(2026, 10, 1), "Prueba", receipt, SplitMode.INDIVIDUAL, null, null);
    }
    String receipt(long org, long owner, boolean active) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO receipts(id,organization_id,uploaded_by_user_id,storage_path,original_filename,content_type,file_size_bytes,is_active) VALUES(?,?,?,'synthetic.png','synthetic.png','image/png',10,?)", id, org, owner, active);
        return id;
    }
    void assertRejected(String id, HttpStatus expected) {
        int before = jdbc.queryForObject("SELECT count(*) FROM expenses", Integer.class);
        var error = assertThrows(ResponseStatusException.class, () -> service.createExpense(request(id)));
        assertEquals(expected, error.getStatusCode());
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM expenses", Integer.class));
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM expense_splits", Integer.class));
    }

    @Test void legacyUrlAndManualEntryStillSave() {
        String legacy = "https://example.invalid/ticket.png";
        assertEquals(legacy, service.createExpense(request(legacy)).expense().getReceiptUrl());
        assertNull(service.createExpense(request(null)).expense().getReceiptUrl());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM expenses", Integer.class));
    }
    @Test void validUuidLinksWithinTheExpenseTransaction() {
        String id = receipt(1, 1, true);
        var saved = service.createExpense(request(id));
        assertEquals(saved.expense().getId(), jdbc.queryForObject("SELECT expense_id FROM receipts WHERE id=?", Long.class, id));
        assertEquals(new BigDecimal("10.01"), jdbc.queryForObject("SELECT sum(amount) FROM expense_splits", BigDecimal.class));
    }
    @Test void businessErrorsKeepTheirStatusAndRollbackExpenseAndParts() {
        assertRejected(UUID.randomUUID().toString(), HttpStatus.NOT_FOUND);
        assertRejected(receipt(2, 2, true), HttpStatus.NOT_FOUND);
        assertRejected(receipt(1, 1, false), HttpStatus.NOT_FOUND);
        assertRejected(receipt(1, 2, true), HttpStatus.FORBIDDEN);
    }
    @Test void alreadyLinkedReceiptReturnsConflictAndPreservesOriginalExpense() {
        String id = receipt(1, 1, true);
        var original = service.createExpense(request(id));
        assertRejected(id, HttpStatus.CONFLICT);
        assertEquals(original.expense().getId(), jdbc.queryForObject("SELECT expense_id FROM receipts WHERE id=?", Long.class, id));
    }
    @Test void failedSqlAssociationRollsBackExpenseAndParts() {
        String id = receipt(1, 1, true);
        jdbc.execute("CREATE OR REPLACE FUNCTION e42_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Fallo sintético'; END $$");
        jdbc.execute("CREATE TRIGGER e42_receipt_failure BEFORE UPDATE ON receipts FOR EACH ROW EXECUTE FUNCTION e42_fail()");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.createExpense(request(id)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM expenses", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM expense_splits", Integer.class));
        assertNull(jdbc.queryForObject("SELECT expense_id FROM receipts WHERE id=?", Long.class, id));
    }
    @Test void concurrentCreatesWithSameReceiptHaveOneWinner() throws Exception {
        String id = receipt(1, 1, true);
        var gate = new CyclicBarrier(2);
        Callable<Boolean> create = () -> {
            gate.await(5, TimeUnit.SECONDS);
            try { service.createExpense(request(id)); return true; }
            catch (ResponseStatusException error) { assertEquals(HttpStatus.CONFLICT, error.getStatusCode()); return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(create); var b = executor.submit(create);
            assertTrue(a.get(10, TimeUnit.SECONDS) ^ b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM expenses", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM expense_splits", Integer.class));
    }
}
