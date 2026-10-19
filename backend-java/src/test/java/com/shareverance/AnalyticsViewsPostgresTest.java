package com.shareverance;

import com.shareverance.controller.AnalyticsCoverageController;
import com.shareverance.dto.*;
import com.shareverance.exception.GlobalExceptionHandler;
import com.shareverance.repository.*;
import com.shareverance.repository.jdbc.*;
import com.shareverance.security.BearerTokenAuthFilter;
import com.shareverance.service.*;
import com.shareverance.service.impl.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="E31_TEST_JDBC_URL", matches=".*shareverance_e12_equal_test.*")
class AnalyticsViewsPostgresTest {
    static String url = System.getenv("E31_TEST_JDBC_URL");
    static YearMonth closed = YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusMonths(1);
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate owner, runtime;
    static MockMvc mvc;
    static String admin, user, other;

    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(url, "shareverance_java", "e31-java-test"); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean AuthService auth(UserRepository users, UserOrganizationRepository members, OrganizationRepository orgs,
                               ExpenseRepository expenses, CategoryRepository categories) {
            return new AuthServiceImpl(users,new BCryptPasswordEncoder(),members,orgs,expenses,categories,null,"python","test-only");
        }
    }

    @BeforeAll static void prepare() {
        var ds = new DriverManagerDataSource(url,"e12","e12-local-test");
        owner = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("6").load().migrate();
        var encoder = new BCryptPasswordEncoder();
        for(int i=1;i<=3;i++) owner.update("INSERT INTO users(id,full_name,email,password_hash) VALUES(?,?,?,?)",
                i,"Prueba "+i,"e31-"+i+"@example.invalid",encoder.encode("test-password"));
        owner.update("INSERT INTO organizations(id,name,type) VALUES(1,'A','HOUSEHOLD'),(2,'B','HOUSEHOLD')");
        owner.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES(11,1,1,'ADMIN'),(12,2,1,'USER'),(21,3,2,'ADMIN')");
        owner.update("INSERT INTO categories(id,organization_id,name,is_active) VALUES(11,1,'Activa',TRUE),(12,1,'Histórica',FALSE),(21,2,'B',TRUE)");
        for(int id : new int[]{11,12,13,14,21}) {
            owner.update("INSERT INTO expenses(id,organization_id,created_by_user_id,paid_by_membership_id,category_id,amount,expense_date,description,is_active) VALUES(?,?,?,?,?,?,?,?,?)",
                    id,id==21?2:1,id==21?3:1,id==21?21:11,id==12?12:id==21?21:11,
                    new BigDecimal(id==11?"10.01":id==12?"5.00":id==13?"99.00":id==14?"7.00":"500.00"),
                    java.sql.Date.valueOf((id==14?closed.minusMonths(1):closed).atDay(1)),"Prueba "+id,id!=13);
        }
        owner.update("INSERT INTO expense_splits(organization_id,expense_id,user_organization_id,amount) VALUES(1,11,11,5.01),(1,11,12,5.00),(1,12,12,5),(1,13,11,99),(1,14,11,7),(2,21,21,500)");
        owner.update("UPDATE user_organization SET is_active=FALSE WHERE id=12");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class,JdbcUserRepository.class,JdbcOrganizationRepository.class,JdbcUserOrganizationRepository.class,
                JdbcExpenseRepository.class,JdbcExpenseSplitRepository.class,JdbcCategoryRepository.class,
                AnalyticsCoverageService.class,ExpenseServiceImpl.class);
        context.refresh(); runtime = context.getBean(JdbcTemplate.class);
        var auth = context.getBean(AuthService.class);
        admin = auth.login(new LoginRequest("e31-1@example.invalid","test-password")).token();
        // Usuario activo para verificar permisos; la inactividad histórica se comprueba en otra prueba.
        owner.update("UPDATE user_organization SET is_active=TRUE WHERE id=12");
        user = auth.login(new LoginRequest("e31-2@example.invalid","test-password")).token();
        other = auth.login(new LoginRequest("e31-3@example.invalid","test-password")).token();
        mvc = MockMvcBuilders.standaloneSetup(new AnalyticsCoverageController(context.getBean(AnalyticsCoverageService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new BearerTokenAuthFilter(auth)).build();
    }
    @BeforeEach void resetCoverage() { owner.update("DELETE FROM analytics_month_coverage"); }
    @AfterAll static void finish() { if(context!=null) context.close(); }

    static Connection python() throws SQLException { return DriverManager.getConnection(url,"shareverance_python","e31-python-test"); }
    static void scope(Connection c,String id) throws SQLException {
        try(var statement=c.prepareStatement("SELECT set_config('shareverance.organization_id',?,TRUE)")) { statement.setString(1,id); statement.execute(); }
    }
    static long scalar(Connection c,String sql) throws SQLException {
        try(var statement=c.createStatement();var result=statement.executeQuery(sql)) { result.next(); return result.getLong(1); }
    }
    static void denied(Connection c,String sql) {
        var ex=assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.execute(sql);}});
        assertEquals("42501",ex.getSQLState(),sql);
    }

    @Test void incrementalMigrationPreservesExistingRowsAndDoesNotInventCoverage() {
        assertEquals("Prueba 11",owner.queryForObject("SELECT description FROM expenses WHERE id=11",String.class));
        assertEquals(new BigDecimal("10.01"),owner.queryForObject("SELECT amount FROM expenses WHERE id=11",BigDecimal.class));
        assertEquals(0,owner.queryForObject("SELECT count(*) FROM analytics_month_coverage",Integer.class));
        assertEquals(6,owner.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
    }

    @Test void amountsAndParticipationsReconcileIncludingInactiveHistoricalMembers() throws Exception {
        owner.update("UPDATE user_organization SET is_active=FALSE WHERE id=12");
        try(var c=python()) {
            c.setAutoCommit(false); scope(c,"1");
            String period=" WHERE month=DATE '"+closed.atDay(1)+"'";
            assertEquals(1501,scalar(c,"SELECT SUM(amount_minor) FROM analytics.v_monthly_expenses"+period));
            assertEquals(1501,scalar(c,"SELECT SUM(amount_minor) FROM analytics.v_monthly_participations"+period));
            assertEquals(1000,scalar(c,"SELECT SUM(amount_minor) FROM analytics.v_monthly_participations"+period+" AND membership_id=12"));
            assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_expenses WHERE organization_id=2"));
            c.rollback();
        } finally { owner.update("UPDATE user_organization SET is_active=TRUE WHERE id=12"); }
    }

    @Test void noMissingInvalidOrOutOfRangeContextLeaksRows() throws Exception {
        try(var c=python()) {
            assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_expenses"));
            c.setAutoCommit(false);
            for(String id:List.of("","bad","0","-1","999999999999999999999999")) {
                scope(c,id);
                for(String view:List.of("expenses","participations","coverage"))
                    assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_"+view));
            }
            c.rollback();
        }
    }

    @Test void reusedConnectionAndPreparedQueryDiscardContextOnCommitAndRollback() throws Exception {
        try(var c=python();var statement=c.prepareStatement("SELECT COALESCE(SUM(amount_minor),0) FROM analytics.v_monthly_expenses WHERE organization_id=?")) {
            c.setReadOnly(true); c.setAutoCommit(false); scope(c,"1"); statement.setLong(1,2);
            try(var r=statement.executeQuery()) {r.next();assertEquals(0,r.getLong(1));}
            c.commit(); assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_expenses"));
            scope(c,"2"); statement.setLong(1,2);
            try(var r=statement.executeQuery()) {r.next();assertEquals(50000,r.getLong(1));}
            c.rollback(); assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_expenses"));
            scope(c,"1"); assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_expenses WHERE organization_id=2")); c.commit();
        }
    }

    @Test void pythonCannotReadBaseTablesWriteCreateObjectsOrAssumeJavaRole() throws Exception {
        assertFalse(owner.queryForObject("SELECT bool_or(rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR rolinherit) FROM pg_roles WHERE rolname IN ('shareverance_java','shareverance_python')",Boolean.class));
        assertEquals(0,owner.queryForObject("SELECT count(*) FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member WHERE r.rolname IN ('shareverance_java','shareverance_python')",Integer.class));
        try(var c=python()) {
            for(String table:List.of("users","organizations","user_organization","categories","expenses","expense_splits","auth_sessions","analytics_month_coverage","flyway_schema_history"))
                denied(c,"SELECT * FROM public."+table);
            for(String sql:List.of("UPDATE public.expenses SET amount=1", "CREATE TABLE public.forbidden(id int)",
                    "CREATE TABLE analytics.forbidden(id int)","CREATE TEMP TABLE forbidden(id int)","SET ROLE shareverance_java")) denied(c,sql);
            try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM analytics.v_monthly_expenses")) {
                assertEquals(List.of("organization_id","month","category_id","amount_minor"),
                        java.util.stream.IntStream.rangeClosed(1,r.getMetaData().getColumnCount()).mapToObj(i->{try{return r.getMetaData().getColumnName(i);}catch(SQLException e){throw new RuntimeException(e);}}).toList());
            }
        }
    }

    @Test void onlyAdminMayDeclareClosedMonthsAndWithdrawWithoutDeletingMetadata() throws Exception {
        String route="/api/v1/organizations/1/analytics/coverage";
        mvc.perform(put(route+"/"+closed)).andExpect(status().isUnauthorized());
        for(String token:List.of(user,other)) mvc.perform(put(route+"/"+closed).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        for(String month:List.of("bad","0000-01",closed.plusMonths(1).toString(),closed.plusMonths(2).toString()))
            mvc.perform(put(route+"/"+month).header("Authorization","Bearer "+admin)).andExpect(status().isBadRequest());
        mvc.perform(put(route+"/"+closed).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$.confirmed").value(true));
        mvc.perform(put(route+"/"+closed).header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        assertEquals(1,owner.queryForObject("SELECT count(*) FROM analytics_month_coverage",Integer.class));
        assertEquals(1,owner.queryForObject("SELECT declared_by_user_id FROM analytics_month_coverage",Integer.class));
        mvc.perform(delete(route+"/"+closed).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$.confirmed").value(false));
        mvc.perform(get(route).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].confirmed").value(false));
        assertEquals(1,owner.queryForObject("SELECT count(*) FROM analytics_month_coverage",Integer.class));
    }

    @Test void explicitlyCoveredZeroMonthIsDifferentFromUnknownAndWithdrawalHidesIt() throws Exception {
        var service=context.getBean(AnalyticsCoverageService.class);
        var empty=closed.minusMonths(3);
        service.declare(1L,1L,empty.toString(),true);
        try(var c=python()) {
            c.setAutoCommit(false);scope(c,"1");
            assertEquals(1,scalar(c,"SELECT count(*) FROM analytics.v_monthly_coverage"));
            assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_expenses WHERE month=DATE '"+empty.atDay(1)+"'"));
            c.commit();
            service.declare(1L,1L,empty.toString(),false);
            scope(c,"1");assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_coverage"));c.commit();
        }
    }

    @Test void coverageConstraintsRejectNonClosedMonthsAndForeignDeclarant() {
        for(var month:List.of(closed.plusMonths(1).atDay(1),closed.atDay(2)))
            assertThrows(org.springframework.dao.DataAccessException.class,()->owner.update(
                    "INSERT INTO analytics_month_coverage(organization_id,covered_month,declared_by_user_id) VALUES(1,?,1)",java.sql.Date.valueOf(month)));
        assertThrows(org.springframework.dao.DataAccessException.class,()->owner.update(
                "INSERT INTO analytics_month_coverage(organization_id,covered_month,declared_by_user_id) VALUES(1,?,3)",java.sql.Date.valueOf(closed.atDay(1))));
        assertEquals(0,owner.queryForObject("SELECT count(*) FROM analytics_month_coverage",Integer.class));
    }

    @Test void inactiveOrganizationIsInvisibleAndCannotDeclareCoverage() throws Exception {
        owner.update("UPDATE organizations SET is_active=FALSE WHERE id=1");
        try(var c=python()) {
            c.setAutoCommit(false);scope(c,"1");
            assertEquals(0,scalar(c,"SELECT count(*) FROM analytics.v_monthly_expenses"));
            mvc.perform(put("/api/v1/organizations/1/analytics/coverage/"+closed).header("Authorization","Bearer "+admin)).andExpect(status().isForbidden());
            c.rollback();
        } finally {owner.update("UPDATE organizations SET is_active=TRUE WHERE id=1");}
    }

    @Test void javaRuntimeWritesTransactionalExpenseButCannotMigrateOrReadFlyway() {
        assertEquals("shareverance_java",runtime.queryForObject("SELECT current_user",String.class));
        var detail=context.getBean(ExpenseService.class).createExpense(new ExpenseCreateRequest(1L,1L,11L,11L,
                new BigDecimal("1.01"),closed.atEndOfMonth(),"Runtime",null,SplitMode.INDIVIDUAL,null,null));
        assertEquals(detail.expense().getAmount(),detail.splits().stream().map(s->s.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add));
        // No contaminar las agregaciones de otras pruebas.
        owner.update("DELETE FROM expense_splits WHERE expense_id=?",detail.expense().getId());
        owner.update("DELETE FROM expenses WHERE id=?",detail.expense().getId());
        assertFalse(runtime.queryForObject("SELECT has_table_privilege(current_user,'flyway_schema_history','SELECT')",Boolean.class));
        assertThrows(org.springframework.dao.DataAccessException.class,()->runtime.execute("CREATE TABLE public.forbidden_java(id int)"));
    }
}
