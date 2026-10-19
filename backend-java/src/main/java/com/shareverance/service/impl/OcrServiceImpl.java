package com.shareverance.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareverance.dto.InternalOcrRequest;
import com.shareverance.dto.OcrResultResponse;
import com.shareverance.model.Receipt;
import com.shareverance.repository.ReceiptRepository;
import com.shareverance.service.AuthService;
import com.shareverance.service.OcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OcrServiceImpl implements OcrService {

    private static final Logger logger = LoggerFactory.getLogger(OcrServiceImpl.class);

    private record InternalOcrResponse(
            @JsonProperty("contract_version") String contractVersion,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("organization_id") Long organizationId,
            @JsonProperty("receipt_id") String receiptId,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("date") String date,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("description") String description,
            @JsonProperty("raw_text") String rawText,
            @JsonProperty("warnings") List<String> warnings
    ) {}

    private final ReceiptRepository receiptRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final String pythonServiceUrl;
    private final String pythonClientSecret;
    private final long readTimeoutMs;
    private final HttpClient httpClient;

    @Autowired
    public OcrServiceImpl(
            ReceiptRepository receiptRepository,
            AuthService authService,
            @Value("${python.service.url:http://python-service:8000}") String pythonServiceUrl,
            @Value("${security.clients.python-service.client-secret:}") String pythonClientSecret,
            @Value("${python.service.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${python.service.ocr-read-timeout-ms:15000}") long readTimeoutMs
    ) {
        this(
                receiptRepository,
                authService,
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
                pythonServiceUrl,
                pythonClientSecret,
                connectTimeoutMs,
                readTimeoutMs
        );
    }

    public OcrServiceImpl(
            ReceiptRepository receiptRepository,
            AuthService authService,
            ObjectMapper objectMapper,
            String pythonServiceUrl,
            String pythonClientSecret,
            long connectTimeoutMs,
            long readTimeoutMs
    ) {
        this.receiptRepository = receiptRepository;
        this.authService = authService;
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.pythonServiceUrl = pythonServiceUrl.replaceAll("/+$", "");
        this.pythonClientSecret = pythonClientSecret == null ? "" : pythonClientSecret.trim();
        this.readTimeoutMs = readTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
                .build();
    }

    @Override
    public OcrResultResponse processReceiptOcr(Long organizationId, String receiptId, Long userId) {
        if (userId == null || !authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }

        Receipt receipt = receiptRepository.findByIdAndOrganizationId(receiptId, organizationId)
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));

        if (receipt.getExpenseId() == null) {
            boolean isAdmin = authService.isOrganizationAdmin(userId, organizationId);
            if (!isAdmin && !Objects.equals(receipt.getUploadedByUserId(), userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenés permiso para procesar este comprobante");
            }
        }

        String requestId = UUID.randomUUID().toString();
        InternalOcrRequest requestPayload = new InternalOcrRequest(
                "1",
                requestId,
                organizationId,
                receiptId,
                receipt.getStoragePath()
        );

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestPayload);
        } catch (IOException e) {
            logger.error("Error al serializar solicitud OCR interna: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al preparar OCR");
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(pythonServiceUrl + "/internal/v1/ocr"))
                .timeout(Duration.ofMillis(Math.max(500, readTimeoutMs)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson));

        if (!pythonClientSecret.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + pythonClientSecret);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            logger.warn("Fallo o timeout de comunicación con el servicio Python OCR: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de OCR no disponible temporalmente");
        }

        if (response.statusCode() != 200) {
            logger.warn("El servicio Python OCR devolvió HTTP {}: {}", response.statusCode(), response.body());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de OCR no disponible temporalmente");
        }

        try {
            InternalOcrResponse internalResponse = objectMapper.readValue(response.body(), InternalOcrResponse.class);
            return new OcrResultResponse(
                    receiptId,
                    internalResponse.status(),
                    internalResponse.currency(),
                    internalResponse.date(),
                    internalResponse.amount(),
                    internalResponse.description(),
                    internalResponse.warnings() != null ? internalResponse.warnings() : List.of()
            );
        } catch (IOException e) {
            logger.error("Error al deserializar respuesta de Python OCR: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Respuesta incompatible del servicio de OCR");
        }
    }
}
