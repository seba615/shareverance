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
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "E12_TEST_JDBC_URL", matches = ".*shareverance_e12_equal_test.*")
class ReceiptsPostgresTest {
    @TempDir Path storage;

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
        jdbcTemplate.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES(1,1,1,'ADMIN'),(2,1,2,'ADMIN') ON CONFLICT DO NOTHING");
        jdbcTemplate.update("INSERT INTO categories(id,organization_id,name) VALUES(1,1,'Prueba'),(2,2,'Ajena') ON CONFLICT DO NOTHING");
        jdbcTemplate.update("""
                INSERT INTO expenses(id,organization_id,created_by_user_id,paid_by_membership_id,category_id,amount,expense_date,description)
                VALUES(999,1,1,1,1,1.01,CURRENT_DATE,'Prueba'),(1000,2,1,2,2,10,CURRENT_DATE,'Ajena'),
                      (1001,1,1,1,1,2,CURRENT_DATE,'Otra') ON CONFLICT DO NOTHING
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

        assertTrue(repository.updateExpenseId(receiptId, 999L, 1L));
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

    @Test void refusesForeignExpenseAndCannotOverwriteAnAssociation() {
        String id=UUID.randomUUID().toString();
        repository.save(new Receipt(id,1L,1L,"org_1/test.png","test.png","image/png",100L,null,Instant.now(),true));
        assertFalse(repository.updateExpenseId(id,1000L,1L));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                ()->jdbcTemplate.update("UPDATE receipts SET expense_id=1000 WHERE id=?",id));
        assertTrue(repository.updateExpenseId(id,999L,1L));
        assertFalse(repository.updateExpenseId(id,1001L,1L));
        assertEquals(999L,repository.findById(id).orElseThrow().getExpenseId());
    }

    @Test void competingAssociationsHaveOnlyOneWinner() throws Exception {
        String id=UUID.randomUUID().toString();
        repository.save(new Receipt(id,1L,1L,"org_1/test.png","test.png","image/png",100L,null,Instant.now(),true));
        var gate=new java.util.concurrent.CyclicBarrier(2);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{gate.await(5,java.util.concurrent.TimeUnit.SECONDS);return repository.updateExpenseId(id,999L,1L);});
            var b=executor.submit(()->{gate.await(5,java.util.concurrent.TimeUnit.SECONDS);return repository.updateExpenseId(id,1001L,1L);});
            assertTrue(a.get(10,java.util.concurrent.TimeUnit.SECONDS)^b.get(10,java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test void failedSqlAndOuterRollbackRemoveOnlyTheirOwnFiles() throws Exception {
        var admin=jdbcTemplate.getDataSource().getConnection();
        admin.createStatement().execute("SET ROLE shareverance_java");
        var ds=new org.springframework.jdbc.datasource.SingleConnectionDataSource(admin,true);
        try {
            var jdbc=new JdbcTemplate(ds);
            assertEquals("shareverance_java",jdbc.queryForObject("SELECT current_user",String.class));
            var auth=org.mockito.Mockito.mock(com.shareverance.service.AuthService.class);
            org.mockito.Mockito.when(auth.isOrganizationMember(1L,1L)).thenReturn(true);
            org.mockito.Mockito.when(auth.isOrganizationMember(999L,1L)).thenReturn(true);
            var receipts=new JdbcReceiptRepository(jdbc);
            var service=new com.shareverance.service.impl.ReceiptServiceImpl(receipts,
                    new com.shareverance.repository.jdbc.JdbcExpenseRepository(jdbc),auth,storage.toString(),10485760,50000000);
            var bytes=new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
            var file=new org.springframework.mock.web.MockMultipartFile("file","test.png","image/png",bytes.toByteArray());
            var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
            String id=tx.execute(status->{var saved=service.storeReceipt(1L,1L,file);status.setRollbackOnly();return saved.receiptId();});
            assertTrue(receipts.findById(id).isEmpty());
            try(var files=Files.walk(storage)){assertEquals(0,files.filter(Files::isRegularFile).count());}
            // Usuario inexistente: la FK falla después de crear el archivo dentro de la transacción.
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    ()->tx.execute(status->service.storeReceipt(1L,999L,file)));
            try(var files=Files.walk(storage)){assertEquals(0,files.filter(Files::isRegularFile).count());}
        } finally {ds.destroy();}
    }

    @Test void upgradeFromV8PreservesValidRowsAndStopsOnExistingCrossOrganizationRows() {
        String url=System.getenv("E12_TEST_JDBC_URL");
        for(boolean crossed:List.of(false,true)) {
            String schema=crossed?"e41_upgrade_crossed":"e41_upgrade_valid";
            var ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,"e12","e12-local-test");
            Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).target("8").locations("classpath:db/migration").load().migrate();
            var jdbc=new JdbcTemplate(ds);
            jdbc.update("INSERT INTO users(id,full_name,email,password_hash) VALUES(1,'Sintético','upgrade@example.invalid','test')");
            jdbc.update("INSERT INTO organizations(id,name,type) VALUES(1,'A','HOUSEHOLD'),(2,'B','HOUSEHOLD')");
            jdbc.update("INSERT INTO user_organization(id,user_id,organization_id,role) VALUES(1,1,1,'ADMIN'),(2,1,2,'ADMIN')");
            jdbc.update("INSERT INTO categories(id,organization_id,name) VALUES(1,1,'A'),(2,2,'B')");
            jdbc.update("INSERT INTO expenses(id,organization_id,created_by_user_id,paid_by_membership_id,category_id,amount,expense_date,description) VALUES(1,1,1,1,1,1,CURRENT_DATE,'A'),(2,2,1,2,2,2,CURRENT_DATE,'B')");
            jdbc.update("INSERT INTO receipts(id,organization_id,uploaded_by_user_id,storage_path,original_filename,content_type,file_size_bytes,expense_id) VALUES('existing',1,1,'org_1/existing.png','existing.png','image/png',100,?)",crossed?2:1);
            var next=Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();
            if(crossed) {
                assertThrows(org.flywaydb.core.api.FlywayException.class,next::migrate);
                assertEquals(2L,jdbc.queryForObject("SELECT expense_id FROM receipts WHERE id='existing'",Long.class));
                // Reparación explícita únicamente del fixture aislado, nunca de datos de desarrollo.
                jdbc.update("UPDATE receipts SET expense_id=1 WHERE id='existing'");
            }
            next.migrate();
            assertEquals("existing.png",jdbc.queryForObject("SELECT original_filename FROM receipts WHERE id='existing'",String.class));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    ()->jdbc.update("UPDATE receipts SET expense_id=2 WHERE id='existing'"));
        }
    }
}
