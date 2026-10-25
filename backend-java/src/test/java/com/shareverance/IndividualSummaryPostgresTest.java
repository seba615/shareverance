package com.shareverance;

import com.shareverance.controller.IndividualSummaryController;
import com.shareverance.exception.GlobalExceptionHandler;
import com.shareverance.repository.jdbc.*;
import com.shareverance.service.*;
import com.shareverance.service.impl.AuthServiceImpl;
import com.shareverance.security.BearerTokenAuthFilter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="JAVA_SUMMARY_TEST_JDBC_URL", matches=".*shareverance_summary_test.*")
class IndividualSummaryPostgresTest {
    static JdbcTemplate jdbc;
    static IndividualSummaryService service;
    static MockMvc mvc;
    static String token, adminToken;
    @BeforeAll static void prepare() {
        var ds = new DriverManagerDataSource(System.getenv("JAVA_SUMMARY_TEST_JDBC_URL"), "e12", "e12-local-test");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        var encoder = new BCryptPasswordEncoder();
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES (1001,'Resumen A','summary-a@example.test',?),(1002,'Resumen B','summary-b@example.test',?)", encoder.encode("SummaryTest123!"), encoder.encode("SummaryTest123!"));
        jdbc.update("INSERT INTO organizations(id,name,type) VALUES (1001,'Resumen A','HOUSEHOLD'),(1002,'Resumen B','HOUSEHOLD')");
        jdbc.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES (1001,1001,1001,'USER'),(1002,1002,1001,'ADMIN'),(1003,1001,1002,'ADMIN')");
        jdbc.update("INSERT INTO categories(id,organization_id,name,is_active) VALUES (1001,1001,'Actual',true),(1002,1001,'Histórica',false),(1003,1002,'Ajena',true)");
        jdbc.update("""
            INSERT INTO expenses(id,organization_id,created_by_user_id,paid_by_membership_id,category_id,amount,expense_date,description,is_active)
            VALUES (1001,1001,1001,1001,1001,10.01,'2024-01-01','Iguales',true),
                   (1002,1001,1002,1002,1002,20.00,'2024-01-31','Individual',true),
                   (1003,1001,1001,1001,1001,30.00,'2024-01-15','Sin parte propia',true),
                   (1004,1001,1001,1001,1001,99.00,'2024-01-01','Anulado',false),
                   (1005,1002,1001,1003,1003,500.00,'2024-01-01','Otro tenant',true),
                   (1006,1001,1001,1001,1001,7.00,'2024-02-01','Otro mes',true)
            """);
        jdbc.update("""
            INSERT INTO expense_splits(organization_id,expense_id,user_organization_id,amount) VALUES
            (1001,1001,1001,5.01),(1001,1001,1002,5.00),(1001,1002,1001,20.00),
            (1001,1003,1002,30.00),(1001,1004,1001,99.00),(1002,1005,1003,500.00),(1001,1006,1001,7.00)
            """);
        var users = new JdbcUserRepository(jdbc);
        var memberships = new JdbcUserOrganizationRepository(jdbc);
        var auth = new AuthServiceImpl(users, encoder, memberships, new JdbcOrganizationRepository(jdbc),
                new JdbcExpenseRepository(jdbc), new JdbcCategoryRepository(jdbc), null, "python", "summary-test-secret");
        service = new IndividualSummaryService(auth, memberships, new JdbcIndividualSummaryRepository(jdbc));
        token = auth.login(new com.shareverance.dto.LoginRequest("summary-a@example.test", "SummaryTest123!")).token();
        adminToken = auth.login(new com.shareverance.dto.LoginRequest("summary-b@example.test", "SummaryTest123!")).token();
        mvc = MockMvcBuilders.standaloneSetup(new IndividualSummaryController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new BearerTokenAuthFilter(auth)).build();
    }
    @Test void usesAssignedPartsNotAuthorOrHolderAndPreservesHistoricalCategory() {
        var result = service.summarize(1001L, 1001L, LocalDate.of(2024,1,1), LocalDate.of(2024,1,31));
        assertEquals("25.01", result.totalAmount().toPlainString());
        assertEquals(2, result.expenseCount());
        assertEquals(2, result.categories().size());
        assertTrue(result.categories().stream().anyMatch(c -> !c.categoryActive() && c.amount().toPlainString().equals("20.00")));
        assertEquals(result.totalAmount(), result.categories().stream().map(c -> c.amount()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
    }
    @Test void optionalBoundariesAndEmptyPeriod() {
        assertEquals("32.01", service.summarize(1001L,1001L,null,null).totalAmount().toPlainString());
        assertEquals("7.00", service.summarize(1001L,1001L,LocalDate.of(2024,2,1),null).totalAmount().toPlainString());
        var empty = service.summarize(1001L,1001L,null,LocalDate.of(2023,12,31));
        assertEquals(0, empty.expenseCount()); assertTrue(empty.categories().isEmpty());
        assertEquals("0.00", empty.totalAmount().toPlainString());
    }
    @Test void httpUsesSessionAndRejectsInvalidDateOrMethod() throws Exception {
        String path = "/api/v1/organizations/1001/expenses/my-summary";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization","Bearer "+token).param("fromDate","2024-01-01").param("toDate","2024-01-31").param("userId","1002"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.membershipId").value(1001))
            .andExpect(jsonPath("$.totalAmount").value(25.01));
        mvc.perform(get(path).header("Authorization","Bearer "+token).param("fromDate","mal"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(path).header("Authorization","Bearer "+token).param("fromDate","2024-02-01").param("toDate","2024-01-01"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void rejectsForeignOrganizationAndInactiveMembership() throws Exception {
        // El usuario A es miembro de ambas organizaciones: el servicio separa por membresía/tenant.
        assertEquals("500.00",service.summarize(1001L,1002L,null,null).totalAmount().toPlainString());
        jdbc.update("UPDATE user_organization SET is_active=FALSE WHERE id=1001");
        try { mvc.perform(get("/api/v1/organizations/1001/expenses/my-summary").header("Authorization","Bearer "+token))
            .andExpect(status().isForbidden()); }
        finally { jdbc.update("UPDATE user_organization SET is_active=TRUE WHERE id=1001"); }
        mvc.perform(get("/api/v1/organizations/999999/expenses/my-summary").header("Authorization","Bearer "+token))
            .andExpect(status().isForbidden());
    }
    @Test void adminCanReadAnotherMemberIncludingInactiveButNotAnotherTenant() throws Exception {
        String path = "/api/v1/organizations/1001/expenses/my-summary";
        mvc.perform(get(path).header("Authorization","Bearer "+adminToken).param("membershipId","1001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.membershipId").value(1001))
            .andExpect(jsonPath("$.totalAmount").value(32.01));
        mvc.perform(get(path).header("Authorization","Bearer "+adminToken).param("membershipId","1003"))
            .andExpect(status().isForbidden());
        jdbc.update("UPDATE user_organization SET is_active=FALSE WHERE id=1001");
        try { mvc.perform(get(path).header("Authorization","Bearer "+adminToken).param("membershipId","1001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(32.01)); }
        finally { jdbc.update("UPDATE user_organization SET is_active=TRUE WHERE id=1001"); }
    }
    @Test void userCanSelectSelfButCannotReadAnotherMember() throws Exception {
        String path = "/api/v1/organizations/1001/expenses/my-summary";
        mvc.perform(get(path).header("Authorization","Bearer "+token).param("membershipId","1001"))
            .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization","Bearer "+token).param("membershipId","1002"))
            .andExpect(status().isForbidden());
    }

}
