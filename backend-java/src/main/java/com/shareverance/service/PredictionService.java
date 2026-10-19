package com.shareverance.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareverance.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class PredictionService {

    public record InternalPredictionRequest(
            @JsonProperty("contract_version") String contractVersion,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("organization_id") Long organizationId,
            @JsonProperty("target_month") String targetMonth
    ) {}

    public record PredictionResult(
            @JsonProperty("status") String status,
            @JsonProperty("estimated_amount_minor") Long estimatedAmountMinor,
            @JsonProperty("months_used") int monthsUsed,
            @JsonProperty("required_months") int requiredMonths,
            @JsonProperty("method") String method,
            @JsonProperty("model_version") String modelVersion
    ) {}

    public record CategoryPredictionResult(
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("status") String status,
            @JsonProperty("estimated_amount_minor") Long estimatedAmountMinor,
            @JsonProperty("months_used") int monthsUsed,
            @JsonProperty("required_months") int requiredMonths,
            @JsonProperty("method") String method,
            @JsonProperty("model_version") String modelVersion
    ) {}

    public record PredictionResponse(
            @JsonProperty("contract_version") String contractVersion,
            @JsonProperty("request_id") String requestId,
            @JsonProperty("organization_id") Long organizationId,
            @JsonProperty("currency") String currency,
            @JsonProperty("target_month") String targetMonth,
            @JsonProperty("status") String status,
            @JsonProperty("simulated") boolean simulated,
            @JsonProperty("organization") PredictionResult organization,
            @JsonProperty("categories") List<CategoryPredictionResult> categories
    ) {}

    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final ObjectMapper objectMapper;
    private final String pythonServiceUrl;
    private final String pythonClientSecret;
    private final long readTimeoutMs;
    private final HttpClient httpClient;

    @Autowired
    public PredictionService(
            JdbcTemplate jdbc,
            AuthService auth,
            @Value("${python.service.url:http://python-service:8000}") String pythonServiceUrl,
            @Value("${security.clients.python-service.client-secret:}") String pythonClientSecret,
            @Value("${python.service.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${python.service.read-timeout-ms:5000}") long readTimeoutMs
    ) {
        this(
                jdbc,
                auth,
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
                pythonServiceUrl,
                pythonClientSecret,
                connectTimeoutMs,
                readTimeoutMs
        );
    }

    public PredictionService(
            JdbcTemplate jdbc,
            AuthService auth,
            ObjectMapper objectMapper,
            String pythonServiceUrl,
            String pythonClientSecret,
            long connectTimeoutMs,
            long readTimeoutMs
    ) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.objectMapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.pythonServiceUrl = pythonServiceUrl.replaceAll("/+$", "");
        this.pythonClientSecret = pythonClientSecret == null ? "" : pythonClientSecret.trim();
        this.readTimeoutMs = readTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
                .build();
    }

    @Transactional(readOnly = true)
    public PredictionResponse getMonthlyPrediction(Long organizationId, Long userId, String targetMonth) {
        if (userId == null || !auth.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere membresía activa de la organización");
        }

        String resolvedTargetMonth = resolveTargetMonth(organizationId, targetMonth);

        if (pythonClientSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de predicciones no está disponible"
            );
        }

        InternalPredictionRequest payload = new InternalPredictionRequest(
                "2",
                UUID.randomUUID().toString(),
                organizationId,
                resolvedTargetMonth
        );

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonServiceUrl + "/internal/v2/predictions/monthly"))
                    .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + pythonClientSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "El servicio de predicciones no está disponible"
                );
            }
            return objectMapper.readValue(response.body(), PredictionResponse.class);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de predicciones no está disponible"
            );
        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "El servicio de predicciones no está disponible"
            );
        }
    }

    private String resolveTargetMonth(Long organizationId, String targetMonth) {
        if (targetMonth != null && !targetMonth.isBlank()) {
            String trimmed = targetMonth.trim();
            try {
                if (!trimmed.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
                    throw new DateTimeParseException("", "", 0);
                }
                YearMonth parsed = YearMonth.parse(trimmed);
                if (parsed.getYear() < 1) {
                    throw new BusinessRuleException("Formato de mes inválido (YYYY-MM, años 0001 a 9999)");
                }
                return parsed.toString();
            } catch (DateTimeParseException ex) {
                throw new BusinessRuleException("Formato de mes inválido (YYYY-MM, años 0001 a 9999)");
            }
        }

        Date latestConfirmed = jdbc.query(
                "SELECT MAX(covered_month) FROM analytics_month_coverage WHERE organization_id = ? AND is_confirmed = TRUE",
                rs -> rs.next() ? rs.getDate(1) : null,
                organizationId
        );
        if (latestConfirmed != null) {
            return YearMonth.from(latestConfirmed.toLocalDate()).plusMonths(1).toString();
        }
        return YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires")).toString();
    }
}

