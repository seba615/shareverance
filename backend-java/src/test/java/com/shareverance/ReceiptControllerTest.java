package com.shareverance;

import com.shareverance.controller.ReceiptController;
import com.shareverance.dto.ReceiptResponse;
import com.shareverance.dto.ReceiptUploadResponse;
import com.shareverance.exception.GlobalExceptionHandler;
import com.shareverance.security.BearerTokenAuthFilter;
import com.shareverance.service.AuthService;
import com.shareverance.service.ReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReceiptControllerTest {

    private ReceiptService receiptService;
    private AuthService authService;
    private MockMvc mockMvc;

    private final String validToken = "valid-token-123";

    @BeforeEach
    void setUp() {
        receiptService = Mockito.mock(ReceiptService.class);
        authService = Mockito.mock(AuthService.class);

        ReceiptController controller = new ReceiptController(receiptService, authService);
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(authService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(filter)
                .build();

        when(authService.validateToken(validToken)).thenReturn(true);
        when(authService.getAuthenticatedUserId(validToken)).thenReturn(1L);
    }

    @Test
    void uploadReceipt_unauthenticated_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/organizations/1/receipts")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadReceipt_userNotMember_returns403() throws Exception {
        when(authService.isAuthorizedForRequest(validToken, "POST", "/api/v1/organizations/1/receipts"))
                .thenReturn(false);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/organizations/1/receipts")
                        .file(file)
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadReceipt_success_returns201() throws Exception {
        when(authService.isAuthorizedForRequest(validToken, "POST", "/api/v1/organizations/1/receipts"))
                .thenReturn(true);
        when(authService.isOrganizationMember(1L, 1L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", new byte[]{1, 2, 3});

        ReceiptUploadResponse uploadResponse = new ReceiptUploadResponse(
                "rcpt-123", 1L, "ticket.png", "image/png", 3, Instant.now(), "STORED"
        );
        when(receiptService.storeReceipt(eq(1L), eq(1L), any())).thenReturn(uploadResponse);

        mockMvc.perform(multipart("/api/v1/organizations/1/receipts")
                        .file(file)
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptId").value("rcpt-123"))
                .andExpect(jsonPath("$.organizationId").value(1))
                .andExpect(jsonPath("$.originalFilename").value("ticket.png"))
                .andExpect(jsonPath("$.status").value("STORED"));
    }

    @Test
    void downloadReceipt_success_returnsFileBytes() throws Exception {
        when(authService.isAuthorizedForRequest(validToken, "GET", "/api/v1/organizations/1/receipts/rcpt-123"))
                .thenReturn(true);
        when(authService.isOrganizationMember(1L, 1L)).thenReturn(true);

        byte[] content = new byte[]{10, 20, 30};
        ReceiptResponse meta = new ReceiptResponse(
                "rcpt-123", 1L, 1L, "ticket.png", "image/png", 3, null, Instant.now()
        );
        when(receiptService.getReceiptMetadata(1L, "rcpt-123", 1L)).thenReturn(meta);
        when(receiptService.loadReceiptFile(1L, "rcpt-123", 1L))
                .thenReturn(new ByteArrayResource(content));

        mockMvc.perform(get("/api/v1/organizations/1/receipts/rcpt-123")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var disposition = ContentDisposition.parse(result.getResponse().getHeader("Content-Disposition"));
                    assertEquals("inline", disposition.getType());
                    assertEquals("ticket.png", disposition.getFilename());
                })
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(content().bytes(content));
    }

    @Test
    void getMetadata_success_returnsJson() throws Exception {
        when(authService.isAuthorizedForRequest(validToken, "GET", "/api/v1/organizations/1/receipts/rcpt-123/metadata"))
                .thenReturn(true);
        when(authService.isOrganizationMember(1L, 1L)).thenReturn(true);

        ReceiptResponse meta = new ReceiptResponse(
                "rcpt-123", 1L, 1L, "ticket.png", "image/png", 1024, null, Instant.now()
        );
        when(receiptService.getReceiptMetadata(1L, "rcpt-123", 1L)).thenReturn(meta);

        mockMvc.perform(get("/api/v1/organizations/1/receipts/rcpt-123/metadata")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value("rcpt-123"))
                .andExpect(jsonPath("$.fileSizeBytes").value(1024));
    }

    @Test
    void downloadReceipt_notFound_returns404() throws Exception {
        when(authService.isAuthorizedForRequest(validToken, "GET", "/api/v1/organizations/1/receipts/unknown"))
                .thenReturn(true);
        when(authService.isOrganizationMember(1L, 1L)).thenReturn(true);

        when(receiptService.getReceiptMetadata(1L, "unknown", 1L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));

        mockMvc.perform(get("/api/v1/organizations/1/receipts/unknown")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReceipts_success_returnsList() throws Exception {
        when(authService.isAuthorizedForRequest(validToken, "GET", "/api/v1/organizations/1/receipts"))
                .thenReturn(true);
        when(authService.isOrganizationMember(1L, 1L)).thenReturn(true);

        ReceiptResponse r1 = new ReceiptResponse("r1", 1L, 1L, "f1.png", "image/png", 100, null, Instant.now());
        when(receiptService.getReceiptsByOrganization(1L, 1L)).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/organizations/1/receipts")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].receiptId").value("r1"));
    }
}
