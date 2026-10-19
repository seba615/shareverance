package com.shareverance;

import com.shareverance.dto.*;
import com.shareverance.repository.jdbc.*;
import com.shareverance.service.ExpenseService;
import com.shareverance.service.impl.ExpenseServiceImpl;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "E12_TEST_JDBC_URL", matches = ".*shareverance_e12_equal_test.*")
class ExpenseTransactionPostgresTest {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static ExpenseService service;

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("E12_TEST_JDBC_URL"), "e12", "e12-local-test");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }

    @BeforeAll static void prepare() {
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class, JdbcExpenseRepository.class, JdbcExpenseSplitRepository.class,
                JdbcOrganizationRepository.class, JdbcUserRepository.class,
                JdbcUserOrganizationRepository.class, JdbcCategoryRepository.class, ExpenseServiceImpl.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(ExpenseService.class);
        var ds = context.getBean(DataSource.class);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc.update("INSERT INTO users(id, full_name, email, password_hash) VALUES (1,'Prueba A','a@example.invalid','test'),(2,'Prueba B','b@example.invalid','test')");
        jdbc.update("INSERT INTO organizations(id,name,type) VALUES (1,'Prueba E1.2','HOUSEHOLD')");
        jdbc.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES (1,1,1,'ADMIN'),(2,2,1,'USER')");
        jdbc.update("INSERT INTO categories(id,organization_id,name) VALUES (1,1,'Prueba')");

    }

    @AfterAll static void close() { if (context != null) context.close(); }

    @BeforeEach void cleanIsolatedDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS e12_failure ON expense_splits");
        jdbc.execute("DROP TRIGGER IF EXISTS e12_expense_failure ON expenses");
        jdbc.update("UPDATE user_organization SET is_active = TRUE");
        jdbc.update("DELETE FROM expense_splits");
        jdbc.update("DELETE FROM expenses");
        jdbc.update("DELETE FROM user_organization WHERE id > 2");
        jdbc.update("DELETE FROM users WHERE id > 2");
    }

    static ExpenseCreateRequest request() {
        return new ExpenseCreateRequest(1L,1L,1L,1L,new BigDecimal("1.01"),LocalDate.now(),
                "Prueba transaccional",null,SplitMode.ALL_MEMBERS,null,null);
    }

    void failOnSecondPart() {
        jdbc.execute("CREATE OR REPLACE FUNCTION e12_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.user_organization_id = 2 THEN RAISE EXCEPTION 'Fallo inyectado E1.2'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER e12_failure BEFORE INSERT OR UPDATE ON expense_splits FOR EACH ROW EXECUTE FUNCTION e12_fail()");
    }

    @Test void rollsBackExpenseAndFirstPartWhenSecondInsertFails() {
        failOnSecondPart();
        assertThrows(RuntimeException.class, () -> service.createExpense(request()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM expenses", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM expense_splits", Integer.class));
    }

    @Test void rollsBackFirstPartAndExpenseWhenSecondUpdateFails() {
        var saved = service.createExpense(request());
        failOnSecondPart();
        assertThrows(RuntimeException.class, () -> service.updateExpense(saved.expense().getId(),
                new ExpenseUpdateRequest(null,new BigDecimal("2.01"),null,"Cambio",null)));
        assertEquals(new BigDecimal("1.01"), jdbc.queryForObject("SELECT amount FROM expenses",BigDecimal.class));
        assertEquals(List.of(new BigDecimal("0.51"), new BigDecimal("0.50")),
                jdbc.queryForList("SELECT amount FROM expense_splits ORDER BY user_organization_id",BigDecimal.class));
        assertEquals("Prueba transaccional", jdbc.queryForObject("SELECT description FROM expenses",String.class));
    }


    @Test void rollsBackPartsIfExpenseUpdateFailsAfterSavingParts() {
        var saved = service.createExpense(request());
        jdbc.execute("CREATE OR REPLACE FUNCTION e12_expense_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Fallo final E1.2'; END $$");
        jdbc.execute("CREATE TRIGGER e12_expense_failure BEFORE UPDATE ON expenses FOR EACH ROW EXECUTE FUNCTION e12_expense_fail()");
        assertThrows(RuntimeException.class, () -> service.updateExpense(saved.expense().getId(),
                new ExpenseUpdateRequest(null,new BigDecimal("2.01"),null,null,null)));
        assertEquals(new BigDecimal("1.01"),jdbc.queryForObject("SELECT amount FROM expenses",BigDecimal.class));
        assertEquals(List.of(new BigDecimal("0.51"),new BigDecimal("0.50")),
                jdbc.queryForList("SELECT amount FROM expense_splits ORDER BY user_organization_id",BigDecimal.class));
    }

    @Test void preservesInactiveHistoricalParticipantsOnEqualUpdate() {
        var saved = service.createExpense(request());
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES (3,'Nuevo','nuevo@example.invalid','test')");
        jdbc.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES (3,3,1,'USER')");
        jdbc.update("UPDATE user_organization SET is_active = FALSE WHERE id = 2");
        service.updateExpense(saved.expense().getId(),new ExpenseUpdateRequest(null,new BigDecimal("2.01"),null,null,null));
        assertEquals(List.of(1L,2L),jdbc.queryForList("SELECT user_organization_id FROM expense_splits ORDER BY user_organization_id",Long.class));
        assertEquals(List.of(new BigDecimal("1.01"),new BigDecimal("1.00")),
                jdbc.queryForList("SELECT amount FROM expense_splits ORDER BY user_organization_id",BigDecimal.class));
    }


    @Test void refusesEqualUpdateLeavingZeroPartsWithoutChangingData() {
        var saved = service.createExpense(request());
        assertThrows(com.shareverance.exception.BusinessRuleException.class, () -> service.updateExpense(
                saved.expense().getId(),new ExpenseUpdateRequest(null,new BigDecimal("0.01"),null,null,null)));
        assertEquals(new BigDecimal("1.01"),jdbc.queryForObject("SELECT amount FROM expenses",BigDecimal.class));
        assertEquals(List.of(new BigDecimal("0.51"),new BigDecimal("0.50")),
                jdbc.queryForList("SELECT amount FROM expense_splits ORDER BY user_organization_id",BigDecimal.class));
    }

    @Test void individualUpdateKeepsOnlyItsHistoricalParticipant() {
        var saved = service.createExpense(new ExpenseCreateRequest(1L,1L,1L,1L,new BigDecimal("1.00"),
                LocalDate.now(),"Individual",null,SplitMode.INDIVIDUAL,null,null));
        service.updateExpense(saved.expense().getId(),new ExpenseUpdateRequest(null,new BigDecimal("2.01"),null,null,null));
        assertEquals("INDIVIDUAL",jdbc.queryForObject("SELECT split_type FROM expenses",String.class));
        assertEquals(List.of(1L),jdbc.queryForList("SELECT user_organization_id FROM expense_splits",Long.class));
        assertEquals(new BigDecimal("2.01"),jdbc.queryForObject("SELECT amount FROM expense_splits",BigDecimal.class));
    }

}
