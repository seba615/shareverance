package com.shareverance.dev;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
public class DevelopmentController {
    private final JdbcTemplate jdbc;
    private final URI pythonHealth;
    private final HttpClient client;
    public DevelopmentController(ObjectProvider<JdbcTemplate> jdbcProvider, @Value("${python.service.url}") String pythonUrl) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.pythonHealth = URI.create(pythonUrl + "/health");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
    public record Status(String java, String database, long demoRows, String python) {}
    @GetMapping("/api/v1/dev/status")
    public Status status() {
        Long count = 0L;
        String dbStatus = "MOCK";
        if (jdbc != null) {
            try {
                jdbc.queryForObject("SELECT 1", Integer.class);
                count = jdbc.queryForObject("SELECT count(*) FROM infra_demo", Long.class);
                dbStatus = "UP";
            } catch (Exception e) {
                dbStatus = "DOWN";
            }
        }
        String python = "UNAVAILABLE";
        try {
            var request = HttpRequest.newBuilder(pythonHealth).timeout(Duration.ofSeconds(3)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) python = "UP";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.io.IOException e) {
            // Una caída de Python no impide consultar la base.
        }
        return new Status("UP", dbStatus, count == null ? 0 : count, python);
    }
}
