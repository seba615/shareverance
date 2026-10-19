package com.shareverance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareverance.dto.OcrResultResponse;
import com.shareverance.model.Receipt;
import com.shareverance.repository.ReceiptRepository;
import com.shareverance.service.AuthService;
import com.shareverance.service.impl.OcrServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OcrServiceTest {

    private ReceiptRepository receiptRepository;
    private AuthService authService;
    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private OcrServiceImpl ocrService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        receiptRepository = Mockito.mock(ReceiptRepository.class);
        authService = Mockito.mock(AuthService.class);
        httpClient = Mockito.mock(HttpClient.class);
        httpResponse = (HttpResponse<String>) Mockito.mock(HttpResponse.class);

        // Instanciar OcrServiceImpl inyectando mockHttpClient mediante reflexión o subclase para pruebas
        ocrService = new OcrServiceImpl(
                receiptRepository,
                authService,
                objectMapper,
                "http://python-service:8000",
                "secret-123",
                2000,
                15000
        ) {
            @Override
            public OcrResultResponse processReceiptOcr(Long organizationId, String receiptId, Long userId) {
                // Delegar con inyección de cliente simulado si es necesario, o usar reflexión
                return super.processReceiptOcr(organizationId, receiptId, userId);
            }
        };

        // Inyectar el httpClient mockeado en el campo privado httpClient
        try {
            var field = OcrServiceImpl.class.getDeclaredField("httpClient");
            field.setAccessible(true);
            field.set(ocrService, httpClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Receipt createReceipt(String id, Long orgId, Long userId, Long expenseId) {
        return new Receipt(
                id,
                orgId,
                userId,
                "org_" + orgId + "/" + id + ".png",
                "ticket.png",
                "image/png",
                12500L,
                expenseId,
                Instant.now(),
                true
        );
    }

    @Test
    void processReceiptOcr_Success() throws Exception {
        Long orgId = 1L;
        Long userId = 10L;
        String receiptId = "rec-uuid-123";

        Receipt receipt = createReceipt(receiptId, orgId, userId, null);

        when(authService.isOrganizationMember(userId, orgId)).thenReturn(true);
        when(receiptRepository.findByIdAndOrganizationId(receiptId, orgId)).thenReturn(Optional.of(receipt));

        String pythonJson = """
                {
                  "contract_version": "1",
                  "request_id": "test-req",
                  "organization_id": 1,
                  "receipt_id": "rec-uuid-123",
                  "currency": "ARS",
                  "status": "OK",
                  "date": "2026-10-15",
                  "amount": 7650.50,
                  "description": "Supermercado compras",
                  "warnings": []
                }
                """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(pythonJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        OcrResultResponse result = ocrService.processReceiptOcr(orgId, receiptId, userId);

        assertNotNull(result);
        assertEquals(receiptId, result.receiptId());
        assertEquals("OK", result.status());
        assertEquals("ARS", result.currency());
        assertEquals("2026-10-15", result.date());
        assertEquals(new BigDecimal("7650.50"), result.amount());
        assertEquals("Supermercado compras", result.description());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void processReceiptOcr_NonMember_ThrowsForbidden() {
        when(authService.isOrganizationMember(10L, 1L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ocrService.processReceiptOcr(1L, "rec-1", 10L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void processReceiptOcr_OtherUserUnassociatedReceipt_NonAdmin_ThrowsForbidden() {
        Long orgId = 1L;
        Long ownerId = 20L;
        Long requestingUserId = 10L;
        String receiptId = "rec-uuid-123";

        Receipt receipt = createReceipt(receiptId, orgId, ownerId, null);

        when(authService.isOrganizationMember(requestingUserId, orgId)).thenReturn(true);
        when(authService.isOrganizationAdmin(requestingUserId, orgId)).thenReturn(false);
        when(receiptRepository.findByIdAndOrganizationId(receiptId, orgId)).thenReturn(Optional.of(receipt));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ocrService.processReceiptOcr(orgId, receiptId, requestingUserId));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void processReceiptOcr_OtherUserUnassociatedReceipt_Admin_Allowed() throws Exception {
        Long orgId = 1L;
        Long ownerId = 20L;
        Long adminUserId = 10L;
        String receiptId = "rec-uuid-123";

        Receipt receipt = createReceipt(receiptId, orgId, ownerId, null);

        when(authService.isOrganizationMember(adminUserId, orgId)).thenReturn(true);
        when(authService.isOrganizationAdmin(adminUserId, orgId)).thenReturn(true);
        when(receiptRepository.findByIdAndOrganizationId(receiptId, orgId)).thenReturn(Optional.of(receipt));

        String pythonJson = """
                {
                  "contract_version": "1",
                  "request_id": "test-req",
                  "organization_id": 1,
                  "receipt_id": "rec-uuid-123",
                  "currency": "ARS",
                  "status": "PARTIAL",
                  "date": null,
                  "amount": 2500.00,
                  "description": "Varios",
                  "warnings": ["No se pudo reconocer la fecha del comprobante."]
                }
                """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(pythonJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        OcrResultResponse result = ocrService.processReceiptOcr(orgId, receiptId, adminUserId);

        assertNotNull(result);
        assertEquals("PARTIAL", result.status());
        assertNull(result.date());
        assertEquals(new BigDecimal("2500.00"), result.amount());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void processReceiptOcr_ReceiptNotFound_ThrowsNotFound() {
        when(authService.isOrganizationMember(10L, 1L)).thenReturn(true);
        when(receiptRepository.findByIdAndOrganizationId("nonexistent", 1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ocrService.processReceiptOcr(1L, "nonexistent", 10L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void processReceiptOcr_PythonReturns503_ThrowsServiceUnavailable() throws Exception {
        Long orgId = 1L;
        Long userId = 10L;
        String receiptId = "rec-uuid-123";

        Receipt receipt = createReceipt(receiptId, orgId, userId, null);

        when(authService.isOrganizationMember(userId, orgId)).thenReturn(true);
        when(receiptRepository.findByIdAndOrganizationId(receiptId, orgId)).thenReturn(Optional.of(receipt));

        when(httpResponse.statusCode()).thenReturn(503);
        when(httpResponse.body()).thenReturn("Service unavailable");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ocrService.processReceiptOcr(orgId, receiptId, userId));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void processReceiptOcr_PythonThrowsIoException_ThrowsServiceUnavailable() throws Exception {
        Long orgId = 1L;
        Long userId = 10L;
        String receiptId = "rec-uuid-123";

        Receipt receipt = createReceipt(receiptId, orgId, userId, null);

        when(authService.isOrganizationMember(userId, orgId)).thenReturn(true);
        when(receiptRepository.findByIdAndOrganizationId(receiptId, orgId)).thenReturn(Optional.of(receipt));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ocrService.processReceiptOcr(orgId, receiptId, userId));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }
}
