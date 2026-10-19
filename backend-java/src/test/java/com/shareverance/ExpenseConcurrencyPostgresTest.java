package com.shareverance;

import com.shareverance.controller.ExpenseController;
import com.shareverance.dto.*;
import com.shareverance.exception.*;
import com.shareverance.model.MembershipRole;
import com.shareverance.repository.*;
import com.shareverance.repository.jdbc.*;
import com.shareverance.service.*;
import com.shareverance.service.impl.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "E12_TEST_JDBC_URL", matches = ".*shareverance_e12_equal_test.*")
@Timeout(30)
class ExpenseConcurrencyPostgresTest {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static ExpenseService expenses;
    static MembershipService memberships;
    static volatile Consumer<String> beforeExpense, afterExpense, beforeOrganization, afterOrganization;

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getenv("E12_TEST_JDBC_URL");
            return new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?")
                    + "currentSchema=e13_concurrency", "e12", "e12-local-test");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        // Sólo se intercalan llamadas: lecturas, escrituras y bloqueos son JDBC reales.
        @Bean @Primary ExpenseRepository expenseHooks(JdbcExpenseRepository delegate) {
            return proxy(ExpenseRepository.class, delegate, true);
        }
        @Bean @Primary OrganizationRepository organizationHooks(JdbcOrganizationRepository delegate) {
            return proxy(OrganizationRepository.class, delegate, false);
        }
    }

    static <T> T proxy(Class<T> type, T delegate, boolean expense) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            Consumer<String> before = expense ? beforeExpense : beforeOrganization;
            Consumer<String> after = expense ? afterExpense : afterOrganization;
            if (before != null) before.accept(method.getName());
            Object result;
            try { result = method.invoke(delegate, args); }
            catch (InvocationTargetException ex) { throw ex.getCause(); }
            if (after != null) after.accept(method.getName());
            return result;
        }));
    }

    @BeforeAll static void prepare() {
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class, JdbcExpenseRepository.class, JdbcExpenseSplitRepository.class,
                JdbcOrganizationRepository.class, JdbcUserRepository.class, JdbcUserOrganizationRepository.class,
                JdbcCategoryRepository.class, ExpenseServiceImpl.class, MembershipServiceImpl.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        expenses = context.getBean(ExpenseService.class);
        memberships = context.getBean(MembershipService.class);
        Flyway.configure().dataSource(context.getBean(DataSource.class))
                .schemas("e13_concurrency").defaultSchema("e13_concurrency")
                .locations("classpath:db/migration").load().migrate();
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES (1,'Prueba A','a@example.invalid','test'),(2,'Prueba B','b@example.invalid','test') ON CONFLICT (id) DO NOTHING");
        jdbc.update("INSERT INTO organizations(id,name,type) VALUES (1,'Prueba E1.3','HOUSEHOLD') ON CONFLICT (id) DO NOTHING");
        jdbc.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES (1,1,1,'ADMIN'),(2,2,1,'ADMIN') ON CONFLICT (id) DO NOTHING");
        jdbc.update("INSERT INTO categories(id,organization_id,name) VALUES (1,1,'Prueba') ON CONFLICT (id) DO NOTHING");
    }

    @BeforeEach void reset() {
        beforeExpense = afterExpense = beforeOrganization = afterOrganization = null;
        jdbc.update("DELETE FROM expense_splits");
        jdbc.update("DELETE FROM expenses");
        jdbc.update("UPDATE user_organization SET role='ADMIN',is_active=TRUE");
    }
    @AfterAll static void close() { if (context != null) context.close(); }

    long create() {
        return expenses.createExpense(new ExpenseCreateRequest(1L,1L,1L,1L,new BigDecimal("1.00"),
                LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")),"Inicial",null,SplitMode.ALL_MEMBERS,null,null)).expense().getId();
    }
    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "No se alcanzó el punto de sincronización"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }
    static boolean thread(String name) { return Thread.currentThread().getName().equals(name); }
    static void awaitDatabaseLock(int pid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid))) return;
            Thread.sleep(10);
        }
        fail("La segunda conexión no esperó el bloqueo real de PostgreSQL");
    }
    void consistent(long id, String amount, int version, boolean active) {
        assertEquals(new BigDecimal(amount), jdbc.queryForObject("SELECT amount FROM expenses WHERE id=?", BigDecimal.class,id));
        assertEquals(new BigDecimal(amount), jdbc.queryForObject("SELECT sum(amount) FROM expense_splits WHERE expense_id=?", BigDecimal.class,id));
        assertEquals(version, jdbc.queryForObject("SELECT version FROM expenses WHERE id=?", Integer.class,id));
        assertEquals(active, jdbc.queryForObject("SELECT is_active FROM expenses WHERE id=?", Boolean.class,id));
    }
    int edit(long id, String amount, int version) {
        try { expenses.updateExpense(id, new ExpenseUpdateRequest(null,new BigDecimal(amount),null,null,null,version)); return 200; }
        catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }

    @Test void twoEditorsWithSameVersionCommitOnce() throws Exception {
        long id = create();
        CyclicBarrier bothRead = new CyclicBarrier(2);
        afterExpense = name -> {
            if (name.equals("findById") && Thread.currentThread().getName().startsWith("editor")) {
                try { bothRead.await(5, TimeUnit.SECONDS); }
                catch (Exception ex) { throw new AssertionError(ex); }
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { Thread.currentThread().setName("editor-a"); return edit(id,"2.00",1); });
            var b = pool.submit(() -> { Thread.currentThread().setName("editor-b"); return edit(id,"3.00",1); });
            assertEquals(609, a.get(10,TimeUnit.SECONDS) + b.get(10,TimeUnit.SECONDS));
        }
        String amount = jdbc.queryForObject("SELECT amount::text FROM expenses WHERE id=?",String.class,id);
        consistent(id,amount,2,true);
    }

    @Test void annulAfterStaleReadPreservesCommittedEdit() throws Exception {
        long id = create();
        CountDownLatch read = new CountDownLatch(1), resume = new CountDownLatch(1);
        afterExpense = name -> { if (name.equals("findById") && thread("annul")) { read.countDown(); await(resume); } };
        try (var pool = Executors.newSingleThreadExecutor()) {
            var task = pool.submit(() -> { Thread.currentThread().setName("annul"); return expenses.deactivateExpense(id); });
            try { await(read); assertEquals(200,edit(id,"2.00",1)); }
            finally { resume.countDown(); }
            assertEquals(3,task.get(10,TimeUnit.SECONDS).getVersion());
        }
        consistent(id,"2.00",3,false);
    }

    @Test void annulBeforeEditWriteCauses409AndNoPartialParts() throws Exception {
        long id = create();
        CountDownLatch read = new CountDownLatch(1), resume = new CountDownLatch(1);
        afterExpense = name -> { if (name.equals("findById") && thread("editor-paused")) { read.countDown(); await(resume); } };
        try (var pool = Executors.newSingleThreadExecutor()) {
            var task = pool.submit(() -> { Thread.currentThread().setName("editor-paused"); return edit(id,"2.00",1); });
            try { await(read); expenses.deactivateExpense(id); }
            finally { resume.countDown(); }
            assertEquals(409,task.get(10,TimeUnit.SECONDS));
        }
        consistent(id,"1.00",2,false);
    }

    @Test void annulWaitsForEditTransactionAndKeepsItsParts() throws Exception {
        long id = create();
        CountDownLatch written = new CountDownLatch(1), resume = new CountDownLatch(1), annulAttempt = new CountDownLatch(1);
        AtomicInteger annulPid = new AtomicInteger();
        afterExpense = name -> { if (name.equals("updateWithOptimisticLocking") && thread("editor-held")) { written.countDown(); await(resume); } };
        beforeExpense = name -> {
            if (name.equals("deactivateById") && thread("annul")) {
                annulPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class)); annulAttempt.countDown();
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var editor = pool.submit(() -> { Thread.currentThread().setName("editor-held"); return edit(id,"2.00",1); });
            await(written);
            var annul = pool.submit(() -> { Thread.currentThread().setName("annul"); return expenses.deactivateExpense(id); });
            try { await(annulAttempt); awaitDatabaseLock(annulPid.get()); }
            finally { resume.countDown(); }
            assertEquals(200,editor.get(10,TimeUnit.SECONDS));
            assertFalse(annul.get(10,TimeUnit.SECONDS).getIsActive());
        }
        consistent(id,"2.00",3,false);
    }

    boolean changeAdmin(long id, boolean demote) {
        try {
            if (demote) memberships.updateMemberRole(id,MembershipRole.USER); else memberships.deactivateMembership(id);
            return true;
        } catch (BusinessRuleException ex) { return false; }
    }
    void concurrentAdmins(boolean firstDemote, boolean secondDemote) throws Exception {
        CountDownLatch locked = new CountDownLatch(1), resume = new CountDownLatch(1), secondAttempt = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        afterOrganization = name -> { if (name.equals("findByIdForUpdate") && thread("admin-first")) { locked.countDown(); await(resume); } };
        beforeOrganization = name -> {
            if (name.equals("findByIdForUpdate") && thread("admin-second")) {
                secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class)); secondAttempt.countDown();
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { Thread.currentThread().setName("admin-first"); return changeAdmin(1L,firstDemote); });
            await(locked);
            var second = pool.submit(() -> { Thread.currentThread().setName("admin-second"); return changeAdmin(2L,secondDemote); });
            try { await(secondAttempt); awaitDatabaseLock(secondPid.get()); }
            finally { resume.countDown(); }
            assertTrue(first.get(10,TimeUnit.SECONDS));
            assertFalse(second.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM user_organization WHERE role='ADMIN' AND is_active",Integer.class));
    }
    @Test void twoAdminDeactivationsLeaveOneAdmin() throws Exception { concurrentAdmins(false,false); }
    @Test void twoAdminDemotionsLeaveOneAdmin() throws Exception { concurrentAdmins(true,true); }
    @Test void mixedAdminChangesLeaveOneAdmin() throws Exception { concurrentAdmins(false,true); }

    @Test void httpRejectsMissingNullAndNonpositiveVersionWithoutChanges() throws Exception {
        long id = create();
        var mvc = MockMvcBuilders.standaloneSetup(new ExpenseController(expenses,mock(AuthService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String body : new String[]{"{\"description\":\"Cambio\"}","{\"version\":null}","{\"version\":0}","{\"version\":-1}"}) {
            mvc.perform(put("/api/v1/expenses/"+id).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        consistent(id,"1.00",1,true);
    }

    @Test void httpReturnsNewVersionAnd409ForStaleEdit() throws Exception {
        long id = create();
        var mvc = MockMvcBuilders.standaloneSetup(new ExpenseController(expenses,mock(AuthService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put("/api/v1/expenses/"+id).contentType("application/json").content("{\"amount\":2.00,\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expense.version").value(2));
        mvc.perform(put("/api/v1/expenses/"+id).contentType("application/json").content("{\"amount\":3.00,\"version\":1}"))
                .andExpect(status().isConflict());
        consistent(id,"2.00",2,true);
    }
}
