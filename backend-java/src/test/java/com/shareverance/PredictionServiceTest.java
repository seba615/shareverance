package com.shareverance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.service.AuthService;
import com.shareverance.service.PredictionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.server.ResponseStatusException;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PredictionServiceTest {

    private HttpServer pythonMockServer;
    private String baseUrl;
    private JdbcTemplate jdbc;
    private AuthService auth;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        pythonMockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + pythonMockServer.getAddress().getPort();
        jdbc = mock(JdbcTemplate.class);
        auth = mock(AuthService.class);
    }

    @AfterEach
    void tearDown() {
        if (pythonMockServer != null) {
            pythonMockServer.stop(0);
        }
    }

    @Test
    void sendsAuthenticatedV2RequestAndResolvesDefaultTargetMonthFromConfirmedCoverage() throws Exception {
        when(auth.isOrganizationMember(5L, 1L)).thenReturn(true);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(1L)))
                .thenReturn(Date.valueOf(LocalDate.of(2026, 9, 1)));

        AtomicReference<String> receivedAuth = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();

        pythonMockServer.createContext("/internal/v2/predictions/monthly", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            JsonNode req = objectMapper.readTree(receivedBody.get());
            String responseJson = """
                    {
                      "contract_version": "2",
                      "request_id": "%s",
                      "organization_id": 1,
                      "currency": "ARS",
                      "target_month": "%s",
                      "status": "OK",
                      "simulated": true,
                      "organization": {
                        "status": "OK",
                        "estimated_amount_minor": 18500000,
                        "months_used": 6,
                        "required_months": 6,
                        "method": "mock",
                        "model_version": "1"
                      },
                      "categories": [
                        {
                          "category_id": 1,
                          "status": "OK",
                          "estimated_amount_minor": 12000000,
                          "months_used": 6,
                          "required_months": 6,
                          "method": "mock",
                          "model_version": "1"
                        }
                      ]
                    }
                    """.formatted(req.get("request_id").asText(), req.get("target_month").asText());
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        pythonMockServer.start();

        PredictionService service = new PredictionService(
                jdbc, auth, objectMapper, baseUrl, "shared-internal-secret", 1000, 2000
        );

        var response = service.getMonthlyPrediction(1L, 5L, null);

        assertEquals("Bearer shared-internal-secret", receivedAuth.get());
        JsonNode sent = objectMapper.readTree(receivedBody.get());
        assertEquals("2", sent.get("contract_version").asText());
        assertEquals(1L, sent.get("organization_id").asLong());
        assertEquals("2026-10", sent.get("target_month").asText());

        assertNotNull(response);
        assertEquals("2", response.contractVersion());
        assertEquals("2026-10", response.targetMonth());
        assertEquals("OK", response.status());
        assertTrue(response.simulated());
        assertEquals(18500000L, response.organization().estimatedAmountMinor());
        assertEquals(1, response.categories().size());
        assertEquals(1L, response.categories().get(0).categoryId());
    }

    @Test
    void rejectsNonMembersAndInvalidTargetMonth() {
        when(auth.isOrganizationMember(9L, 1L)).thenReturn(false);
        PredictionService service = new PredictionService(
                jdbc, auth, objectMapper, baseUrl, "shared-internal-secret", 1000, 2000
        );

        var forbidden = assertThrows(ResponseStatusException.class,
                () -> service.getMonthlyPrediction(1L, 9L, "2026-10"));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        when(auth.isOrganizationMember(5L, 1L)).thenReturn(true);
        assertThrows(BusinessRuleException.class,
                () -> service.getMonthlyPrediction(1L, 5L, "2026-13"));
        assertThrows(BusinessRuleException.class,
                () -> service.getMonthlyPrediction(1L, 5L, "0000-05"));
    }

    @Test
    void translatesTimeoutOrUpstreamErrorInto503ServiceUnavailable() {
        when(auth.isOrganizationMember(5L, 1L)).thenReturn(true);
        pythonMockServer.createContext("/internal/v2/predictions/monthly", exchange -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        pythonMockServer.start();

        PredictionService service = new PredictionService(
                jdbc, auth, objectMapper, baseUrl, "shared-internal-secret", 200, 200
        );

        var unavailable = assertThrows(ResponseStatusException.class,
                () -> service.getMonthlyPrediction(1L, 5L, "2026-10"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
    }
}

