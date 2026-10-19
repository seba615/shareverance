package com.shareverance;

import com.shareverance.dto.*;
import com.shareverance.exception.*;
import com.shareverance.repository.*;
import com.shareverance.repository.jdbc.*;
import com.shareverance.service.*;
import com.shareverance.service.impl.*;
import com.shareverance.controller.ExpenseController;
import com.shareverance.security.BearerTokenAuthFilter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="E12_TEST_JDBC_URL",matches=".*shareverance_e12_equal_test.*")
class ExpenseHistoryPostgresTest {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static ExpenseService service;
    static AuthServiceImpl auth;
    static MockMvc mvc;
    static String userToken, adminToken;
    static LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(System.getenv("E12_TEST_JDBC_URL")+"?currentSchema=e22_history","e12","e12-local-test"); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
    @BeforeAll static void prepare() {
        context=new AnnotationConfigApplicationContext();
        context.register(Config.class,JdbcExpenseRepository.class,JdbcExpenseSplitRepository.class,
                JdbcOrganizationRepository.class,JdbcUserRepository.class,JdbcUserOrganizationRepository.class,
                JdbcCategoryRepository.class,ExpenseServiceImpl.class);context.refresh();
        jdbc=context.getBean(JdbcTemplate.class);service=context.getBean(ExpenseService.class);
        Flyway.configure().dataSource(context.getBean(DataSource.class)).schemas("e22_history").defaultSchema("e22_history")
                .locations("classpath:db/migration").load().migrate();
        var encoder=new BCryptPasswordEncoder();
        for(int i=1;i<=3;i++) jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES (?,?,?,?)",i,"Prueba "+i,"u"+i+"@example.invalid",encoder.encode("password123"));
        jdbc.update("INSERT INTO organizations(id,name,type) VALUES (1,'A','HOUSEHOLD'),(2,'B','HOUSEHOLD')");
        jdbc.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES (1,1,1,'ADMIN'),(2,2,1,'USER'),(3,3,2,'ADMIN')");
        jdbc.update("INSERT INTO categories(id,organization_id,name) VALUES (1,1,'Activa'),(2,1,'Histórica'),(3,2,'Ajena')");
        auth=new AuthServiceImpl(context.getBean(UserRepository.class),encoder,context.getBean(UserOrganizationRepository.class),
                context.getBean(OrganizationRepository.class),context.getBean(ExpenseRepository.class),context.getBean(CategoryRepository.class),null,"python","test-secret");
        userToken=auth.login(new LoginRequest("u2@example.invalid","password123")).token();
        adminToken=auth.login(new LoginRequest("u1@example.invalid","password123")).token();
        mvc=MockMvcBuilders.standaloneSetup(new ExpenseController(service,auth)).setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new BearerTokenAuthFilter(auth)).build();
    }
    @AfterAll static void close(){if(context!=null)context.close();}
    @BeforeEach void reset(){jdbc.update("DELETE FROM expense_splits");jdbc.update("DELETE FROM expenses");jdbc.update("UPDATE categories SET is_active=TRUE");jdbc.update("UPDATE user_organization SET is_active=TRUE");}
    long create(int org,int author,int category,String amount,LocalDate date){
        return service.createExpense(new ExpenseCreateRequest((long)org,(long)author,(long)author,(long)category,
                new BigDecimal(amount),date,"Prueba",null,SplitMode.ALL_MEMBERS,null,null)).expense().getId();
    }
    ExpensePageResponse page(ExpenseFilter filter,int page,int size){return service.getExpensePage(1L,filter,page,size);}
    @Test void paginationHasStableOrderNoDuplicateIdsAndFullFilteredTotal(){
        List<Long> ids=new ArrayList<>();for(int i=1;i<=5;i++)ids.add(create(1,1,1,i+".00",today));
        create(2,3,3,"99.00",today);
        var filter=new ExpenseFilter(null,null,null,"ACTIVE");
        var first=page(filter,0,2);var second=page(filter,1,2);var third=page(filter,2,2);
        List<Long> found=new ArrayList<>();for(var p:List.of(first,second,third))p.items().forEach(d->found.add(d.expense().getId()));
        Collections.reverse(ids);assertEquals(ids,found);assertEquals(5,new HashSet<>(found).size());
        assertEquals(5,first.totalElements());assertEquals(3,first.totalPages());assertEquals(new BigDecimal("15.00"),first.totalAmount());
        var empty=page(filter,99,2);assertTrue(empty.items().isEmpty());assertEquals(5,empty.totalElements());assertEquals(new BigDecimal("15.00"),empty.totalAmount());
    }
    @Test void datesAreInclusiveAndCategoryMayBeInactive(){
        create(1,1,1,"1.00",today.minusDays(2));create(1,1,2,"2.00",today.minusDays(1));create(1,1,1,"3.00",today);
        jdbc.update("UPDATE categories SET is_active=FALSE WHERE id=2");
        var result=page(new ExpenseFilter(today.minusDays(1),today.minusDays(1),2L,"ACTIVE"),0,20);
        assertEquals(1,result.totalElements());assertEquals(new BigDecimal("2.00"),result.totalAmount());
    }
    @Test void annulledFiltersAndTotalsDoNotDuplicateExpenseByItsParts(){
        long id=create(1,1,1,"10.00",today);create(1,1,1,"20.00",today);service.deactivateExpense(id);
        assertEquals(new BigDecimal("20.00"),page(new ExpenseFilter(null,null,null,"ACTIVE"),0,20).totalAmount());
        assertEquals(new BigDecimal("10.00"),page(new ExpenseFilter(null,null,null,"ANNULLED"),0,20).totalAmount());
        assertEquals(new BigDecimal("30.00"),page(new ExpenseFilter(null,null,null,"ALL"),0,20).totalAmount());
    }
    @Test void invalidQueryParametersReturn400() throws Exception {
        for(String query:List.of("size=0","size=101","page=-1","page=abc","state=INVALID","fromDate=bad","categoryId=3",
                "fromDate="+today+"&toDate="+today.minusDays(1))) {
            mvc.perform(get("/api/v1/expenses/organization/1/page?"+query).header("Authorization","Bearer "+userToken)).andExpect(status().isBadRequest());
        }
    }
    @Test void pageAndDetailRejectAnotherOrganizationAndAnonymousAccess() throws Exception {
        long foreign=create(2,3,3,"99.00",today);
        mvc.perform(get("/api/v1/expenses/organization/1/page")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/expenses/organization/1/page").header("Authorization","Bearer "+userToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/expenses/organization/2/page").header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/expenses/"+foreign).header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
    }
    @Test void ownerEditRequiresVersionAndStaleEditDoesNotChangeParts() throws Exception {
        long id=create(1,2,1,"10.00",today);
        mvc.perform(put("/api/v1/expenses/"+id).header("Authorization","Bearer "+userToken).contentType("application/json")
                .content("{\"amount\":\"11.00\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/expenses/"+id).header("Authorization","Bearer "+userToken).contentType("application/json")
                .content("{\"amount\":\"11.00\",\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.expense.version").value(2));
        mvc.perform(put("/api/v1/expenses/"+id).header("Authorization","Bearer "+userToken).contentType("application/json")
                .content("{\"amount\":\"12.00\",\"version\":1}")).andExpect(status().isConflict());
        assertEquals(new BigDecimal("11.00"),jdbc.queryForObject("SELECT sum(amount) FROM expense_splits WHERE expense_id=?",BigDecimal.class,id));
    }
    @Test void onlyOwnerOrAdminCanEditAndAnnul() throws Exception {
        long id=create(1,1,1,"10.00",today);
        mvc.perform(put("/api/v1/expenses/"+id).header("Authorization","Bearer "+userToken).contentType("application/json").content("{\"version\":1}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/expenses/"+id).header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/expenses/"+id).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/expenses/"+id).header("Authorization","Bearer "+userToken)).andExpect(status().isOk()).andExpect(jsonPath("$.expense.isActive").value(false));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM expense_splits WHERE expense_id=?",Integer.class,id));
    }
    @Test void editPreservesInactiveHistoricalCategoryAndParticipants(){
        long id=create(1,1,2,"10.00",today);
        jdbc.update("UPDATE categories SET is_active=FALSE WHERE id=2");jdbc.update("UPDATE user_organization SET is_active=FALSE WHERE id=2");
        var updated=service.updateExpense(id,new ExpenseUpdateRequest(2L,new BigDecimal("11.00"),today,"Editado",null,1));
        assertEquals(2,updated.splits().size());assertEquals(2L,updated.expense().getCategoryId());
        long other=create(1,1,1,"10.00",today);
        assertThrows(BusinessRuleException.class,()->service.updateExpense(other,new ExpenseUpdateRequest(2L,null,null,null,null,1)));
    }
    @Test void descriptionAndFutureDateAreRejectedWithoutWrites(){
        long id=create(1,1,1,"10.00",today);
        for(String description:List.of(" ","x".repeat(501)))assertThrows(BusinessRuleException.class,()->service.updateExpense(id,new ExpenseUpdateRequest(null,null,null,description,null,1)));
        assertThrows(BusinessRuleException.class,()->service.updateExpense(id,new ExpenseUpdateRequest(null,null,today.plusDays(1),null,null,1)));
        assertEquals(1,service.getExpenseById(id).expense().getVersion());
    }
    @Test void calendarAggregatesByDayExcludesAnnulledAndDoesNotDuplicateSplits() throws Exception {
        YearMonth ym=YearMonth.from(today);
        LocalDate d1=ym.atDay(1);
        LocalDate d2=today.getDayOfMonth()>1?ym.atDay(today.getDayOfMonth()):d1;
        long active1=create(1,1,1,"10.50",d1);
        create(1,1,1,"20.25",d1);
        long annulled=create(1,1,1,"99.00",d1);
        service.deactivateExpense(annulled);
        create(2,3,3,"500.00",d1);
        create(1,1,1,"7.00",ym.minusMonths(1).atDay(10));
        var cal=service.getExpenseCalendar(1L,ym.toString());
        assertEquals(ym.toString(),cal.month());
        assertEquals(new BigDecimal("30.75"),cal.totalAmount());
        assertEquals(2L,cal.totalExpenses());
        assertEquals(1,cal.days().size());
        assertEquals(d1,cal.days().get(0).date());
        assertEquals(new BigDecimal("30.75"),cal.days().get(0).totalAmount());
        assertEquals(2L,cal.days().get(0).expenseCount());
        service.updateExpense(active1,new ExpenseUpdateRequest(null,new BigDecimal("15.00"),d2,"Movido",null,1));
        var updatedCal=service.getExpenseCalendar(1L,null);
        assertEquals(new BigDecimal("35.25"),updatedCal.totalAmount());
        mvc.perform(get("/api/v1/expenses/organization/1/calendar")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/expenses/organization/2/calendar").header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/expenses/organization/1/calendar?month=bad").header("Authorization","Bearer "+userToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/expenses/organization/1/calendar?month="+ym.plusMonths(1)).header("Authorization","Bearer "+userToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/expenses/organization/1/calendar?month="+ym).header("Authorization","Bearer "+userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(ym.toString()))
                .andExpect(jsonPath("$.totalExpenses").value(2));
    }
}
