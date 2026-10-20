package com.shareverance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.service.AnalyticsService;
import com.shareverance.service.AuthService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {
    private HttpServer server;
    private AuthService auth;
    private AnalyticsService service;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private ObjectNode received;
    private String authorization;
    private Consumer<ObjectNode> alter = body -> {};
    private int status = 200;
    private long delay = 0;

    @BeforeEach void setup() throws Exception {
        auth = mock(AuthService.class);
        when(auth.isOrganizationMember(5L, 1L)).thenReturn(true);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v2/analytics/monthly", exchange -> {
            calls.incrementAndGet();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            received = (ObjectNode) mapper.readTree(exchange.getRequestBody());
            ObjectNode response = response(received);
            alter.accept(response);
            try { Thread.sleep(delay); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            byte[] bytes = mapper.writeValueAsBytes(response);
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        service = service(1500);
    }
    private AnalyticsService service(long timeout) {
        return new AnalyticsService(auth, "http://127.0.0.1:" + server.getAddress().getPort(), "test-secret", 500, timeout);
    }
    @AfterEach void cleanup() { server.stop(0); }

    private ObjectNode response(ObjectNode request) throws java.io.IOException {
        ObjectNode body = (ObjectNode) mapper.readTree("""
          {"contract_version":"2","organization_id":1,"currency":"ARS","status":"OK","simulated":false,
           "comparison":{"previous_month":"2026-01","current_month":"2026-02"},
           "organization":{"status":"OK","previous_amount_minor":0,"current_amount_minor":10001,"change_amount_minor":10001,"change_percentage":null,"direction":"INCREASING"},
           "categories":[{"category_id":10,"status":"OK","previous_amount_minor":0,"current_amount_minor":10001,"change_amount_minor":10001,"change_percentage":null,"direction":"INCREASING"}],
           "evolution":[{"month":"2026-01","covered":true,"total_amount_minor":0,"category_totals":[{"category_id":10,"amount_minor":0}]},
                        {"month":"2026-02","covered":true,"total_amount_minor":10001,"category_totals":[{"category_id":10,"amount_minor":10001}]}]}
          """);
        body.put("request_id", request.path("request_id").asText());
        return body;
    }
    @Test void forwardsAuthorizedContextAndPreservesExactAmountsAndNullPercentage() {
        var response = service.monthly(1L, 5L, "2026-01", "2026-02");
        assertEquals("Bearer test-secret", authorization);
        assertEquals("2", received.path("contract_version").asText());
        assertEquals(1L, received.path("organization_id").asLong());
        assertEquals("2026-01", received.path("from_month").asText());
        assertEquals("2026-02", received.path("to_month").asText());
        assertEquals(10001L, response.organization().current_amount_minor());
        assertNull(response.organization().change_percentage());
        assertFalse(response.simulated());
    }
    @Test void rejectsMembershipBeforeCallingPython() {
        var error = assertThrows(ResponseStatusException.class, () -> service.monthly(2L, 5L, "2026-01", "2026-02"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.monthly(1L, null, null, null));
        assertEquals(0, calls.get());
    }
    @Test void rejectsInvalidAndUnclosedWindowsBeforeCallingPython() {
        for (String[] range : new String[][]{{"0000-01","2026-02"},{"2026-1","2026-02"},{"2026-03","2026-02"},
                {"2020-01","2026-02"},{"2026-13","2026-02"},{"", "2026-02"},{"2026-01","9999-12"}}) {
            assertThrows(BusinessRuleException.class, () -> service.monthly(1L, 5L, range[0], range[1]));
        }
        assertEquals(0, calls.get());
    }
    @Test void defaultWindowUsesSixClosedMonthsInBuenosAires() {
        // La respuesta fija no corresponde a esta ventana: valida la solicitud y rechaza el desajuste.
        assertThrows(ResponseStatusException.class, () -> service.monthly(1L, 5L, null, null));
        YearMonth to = YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusMonths(1);
        assertEquals(to.toString(), received.path("to_month").asText());
        assertEquals(to.minusMonths(5).toString(), received.path("from_month").asText());
    }
    @Test void preservesUnknownCoverageAndInsufficientHistory() {
        alter = body -> {
            body.put("status", "INSUFFICIENT_HISTORY"); body.putNull("comparison");
            for (var result : java.util.List.of(body.withObject("organization"), (ObjectNode) body.path("categories").get(0))) {
                result.put("status", "INSUFFICIENT_HISTORY");
                for (String key : java.util.List.of("previous_amount_minor","current_amount_minor","change_amount_minor","change_percentage","direction")) result.putNull(key);
            }
            var last = (ObjectNode) body.path("evolution").get(1);
            last.put("covered", false); last.putNull("total_amount_minor"); last.putArray("category_totals");
        };
        var response = service.monthly(1L, 5L, "2026-01", "2026-02");
        assertNull(response.comparison()); assertNull(response.evolution().get(1).total_amount_minor());
        assertEquals(0L, response.evolution().get(0).total_amount_minor());
    }
    @Test void rejectsMismatchedIdentityCurrencySimulationAndTotals() {
        for (Consumer<ObjectNode> mutation : java.util.List.<Consumer<ObjectNode>>of(
                body -> body.put("organization_id", 2), body -> body.put("request_id", "other"),
                body -> body.put("currency", "USD"), body -> body.put("simulated", true),
                body -> ((ObjectNode) body.path("evolution").get(1)).put("total_amount_minor", 1),
                body -> body.withObject("organization").put("current_amount_minor", 10),
                body -> ((ObjectNode) body.path("evolution").get(0)).put("month", "2025-01"))) {
            alter = mutation;
            var error = assertThrows(ResponseStatusException.class, () -> service.monthly(1L, 5L, "2026-01", "2026-02"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
            assertFalse(error.getReason().contains("test-secret"));
        }
    }
    @Test void upstreamFailureAndTimeoutAreControlled503() {
        status = 500;
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service.monthly(1L, 5L, "2026-01", "2026-02")).getStatusCode());
        status = 200; delay = 350;
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> service(100).monthly(1L, 5L, "2026-01", "2026-02")).getStatusCode());
    }
}
