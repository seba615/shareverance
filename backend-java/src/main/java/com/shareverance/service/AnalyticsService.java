package com.shareverance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareverance.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsService {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    public record Result(String status, Long previous_amount_minor, Long current_amount_minor,
                         Long change_amount_minor, Double change_percentage, String direction) {}
    public record CategoryResult(long category_id, String status, Long previous_amount_minor,
                                 Long current_amount_minor, Long change_amount_minor,
                                 Double change_percentage, String direction) {}
    public record CategoryTotal(long category_id, long amount_minor) {}
    public record Evolution(String month, boolean covered, Long total_amount_minor,
                            List<CategoryTotal> category_totals) {}
    public record Comparison(String previous_month, String current_month) {}
    public record Response(String contract_version, String request_id, long organization_id, String currency,
                           String status, boolean simulated, Comparison comparison, Result organization,
                           List<CategoryResult> categories, List<Evolution> evolution) {}

    private final AuthService auth;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final String url;
    private final String secret;
    private final long readTimeout;

    public AnalyticsService(AuthService auth,
            @Value("${python.service.url:http://python-service:8000}") String url,
            @Value("${security.clients.python-service.client-secret:}") String secret,
            @Value("${python.service.connect-timeout-ms:2000}") long connectTimeout,
            @Value("${python.service.read-timeout-ms:5000}") long readTimeout) {
        this.auth = auth;
        this.url = url.replaceAll("/+$", "");
        this.secret = secret == null ? "" : secret.trim();
        this.readTimeout = readTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.max(100, connectTimeout))).build();
    }

    public Response monthly(Long organizationId, Long userId, String fromMonth, String toMonth) {
        if (userId == null || !auth.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere membresía activa de la organización");
        }
        YearMonth current = YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        YearMonth to = toMonth == null ? current.minusMonths(1) : parseMonth(toMonth);
        YearMonth from = fromMonth == null ? to.minusMonths(Math.min(5, (to.getYear() - 1L) * 12 + to.getMonthValue() - 1)) : parseMonth(fromMonth);
        long length = ChronoUnit.MONTHS.between(from, to) + 1;
        if (length < 1 || length > 60 || !to.isBefore(current)) {
            throw new BusinessRuleException("El período debe incluir de 1 a 60 meses cerrados, en orden cronológico");
        }
        if (secret.isBlank()) throw unavailable();
        String requestId = UUID.randomUUID().toString();
        var payload = mapper.createObjectNode().put("contract_version", "2").put("request_id", requestId)
                .put("organization_id", organizationId).put("from_month", from.toString()).put("to_month", to.toString());
        try {
            var request = HttpRequest.newBuilder(URI.create(url + "/internal/v2/analytics/monthly"))
                    .timeout(Duration.ofMillis(Math.max(100, readTimeout)))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", "Bearer " + secret)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            var upstream = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (upstream.statusCode() != 200) throw unavailable();
            JsonNode body = mapper.readTree(upstream.body());
            validate(body, organizationId, requestId, from, to, length);
            return mapper.treeToValue(body, Response.class);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | RuntimeException ex) {
            throw unavailable();
        }
    }

    private static YearMonth parseMonth(String value) {
        if (!value.matches("[0-9]{4}-(0[1-9]|1[0-2])") || value.startsWith("0000")) {
            throw new BusinessRuleException("Formato de mes inválido (YYYY-MM, años 0001 a 9999)");
        }
        return YearMonth.parse(value);
    }

    private static void validate(JsonNode body, long organizationId, String requestId,
                                 YearMonth from, YearMonth to, long length) {
        require(body != null && body.isObject());
        require(body.path("organization_id").isIntegralNumber() && body.path("organization_id").asLong() == organizationId
                && "2".equals(body.path("contract_version").asText())
                && requestId.equals(body.path("request_id").asText()) && "ARS".equals(body.path("currency").asText())
                && body.path("simulated").isBoolean() && !body.path("simulated").booleanValue());
        boolean ok = "OK".equals(body.path("status").asText());
        require(ok || "INSUFFICIENT_HISTORY".equals(body.path("status").asText()));
        require(body.path("categories").isArray() && body.path("categories").size() <= 100);
        require(body.path("evolution").isArray() && body.path("evolution").size() == length);
        int index = 0;
        for (JsonNode month : body.path("evolution")) {
            require(from.plusMonths(index++).toString().equals(month.path("month").asText())
                    && month.path("covered").isBoolean() && month.path("category_totals").isArray());
            if (!month.path("covered").booleanValue()) {
                require(month.path("total_amount_minor").isNull() && month.path("category_totals").isEmpty());
            } else {
                long sum = 0;
                var ids = new java.util.HashSet<Long>();
                for (JsonNode category : month.path("category_totals")) {
                    require(category.path("category_id").isIntegralNumber() && category.path("category_id").asLong() > 0
                            && ids.add(category.path("category_id").asLong()));
                    sum = Math.addExact(sum, amount(category.path("amount_minor")));
                }
                require(amount(month.path("total_amount_minor")) == sum);
            }
        }
        validateResult(body.path("organization"), ok);
        var ids = new java.util.HashSet<Long>();
        for (JsonNode category : body.path("categories")) {
            require(category.path("category_id").isIntegralNumber() && category.path("category_id").asLong() > 0
                    && ids.add(category.path("category_id").asLong()));
            validateResult(category, ok);
        }
        if (ok) {
            require(length >= 2 && to.toString().equals(body.path("comparison").path("current_month").asText())
                    && to.minusMonths(1).toString().equals(body.path("comparison").path("previous_month").asText()));
        } else require(body.path("comparison").isNull());
        for (JsonNode month : body.path("evolution")) {
            if (month.path("covered").booleanValue()) {
                var monthIds = new java.util.HashSet<Long>();
                for (JsonNode category : month.path("category_totals")) monthIds.add(category.path("category_id").asLong());
                require(monthIds.equals(ids));
            }
        }
        if (ok) {
            JsonNode previous = body.path("evolution").get((int) length - 2);
            JsonNode current = body.path("evolution").get((int) length - 1);
            require(previous.path("covered").booleanValue() && current.path("covered").booleanValue());
            long previousSum = 0, currentSum = 0;
            for (JsonNode category : body.path("categories")) {
                long id = category.path("category_id").asLong();
                for (JsonNode total : previous.path("category_totals")) if (total.path("category_id").asLong() == id)
                    require(total.path("amount_minor").asLong() == category.path("previous_amount_minor").asLong());
                for (JsonNode total : current.path("category_totals")) if (total.path("category_id").asLong() == id)
                    require(total.path("amount_minor").asLong() == category.path("current_amount_minor").asLong());
                previousSum = Math.addExact(previousSum, category.path("previous_amount_minor").asLong());
                currentSum = Math.addExact(currentSum, category.path("current_amount_minor").asLong());
            }
            require(previousSum == body.path("organization").path("previous_amount_minor").asLong()
                    && currentSum == body.path("organization").path("current_amount_minor").asLong());
        }
    }

    private static void validateResult(JsonNode result, boolean ok) {
        require(result.isObject() && (ok ? "OK" : "INSUFFICIENT_HISTORY").equals(result.path("status").asText()));
        if (!ok) {
            for (String field : List.of("previous_amount_minor", "current_amount_minor", "change_amount_minor", "change_percentage", "direction")) {
                require(result.path(field).isNull());
            }
            return;
        }
        long previous = amount(result.path("previous_amount_minor"));
        long current = amount(result.path("current_amount_minor"));
        require(result.path("change_amount_minor").isIntegralNumber()
                && result.path("change_amount_minor").asLong() == current - previous);
        require((current > previous ? "INCREASING" : current < previous ? "DECREASING" : "UNCHANGED")
                .equals(result.path("direction").asText()));
        require(previous == 0 ? result.path("change_percentage").isNull()
                : result.path("change_percentage").isNumber() && Double.isFinite(result.path("change_percentage").asDouble()));
    }

    private static long amount(JsonNode node) {
        require(node.isIntegralNumber() && node.canConvertToLong() && node.asLong() >= 0 && node.asLong() <= MAX_SAFE_INTEGER);
        return node.asLong();
    }
    private static void require(boolean condition) { if (!condition) throw unavailable(); }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de analítica no está disponible");
    }
}
