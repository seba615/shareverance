package com.shareverance.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shareverance.dto.ClientCredentialsRequest;
import com.shareverance.dto.LoginRequest;
import com.shareverance.dto.LoginResponse;
import com.shareverance.dto.ServiceTokenResponse;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.exception.OAuthTokenException;
import com.shareverance.model.User;
import com.shareverance.repository.UserRepository;
import com.shareverance.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private static final String ANALYTICS_READ_SCOPE = "analytics:read";
    private static final String HISTORY_PATH_PATTERN = "/api/v1/expenses/organization/\\d+/history";

    private final UserRepository userRepository;
    private final String pythonClientId;
    private final String pythonClientSecret;
    private final Map<String, AccessGrant> activeTokens = new ConcurrentHashMap<>();

    public AuthServiceImpl(UserRepository userRepository,
                           @Value("${security.clients.python-service.client-id:}") String pythonClientId,
                           @Value("${security.clients.python-service.client-secret:}") String pythonClientSecret) {
        this.userRepository = userRepository;
        this.pythonClientId = pythonClientId;
        this.pythonClientSecret = pythonClientSecret;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessRuleException("El correo electrónico es requerido");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessRuleException("La contraseña es requerida");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas: correo no encontrado"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("La cuenta de usuario está desactivada");
        }

        // Generar un token aleatorio seguro
        String generatedToken = "usr_" + UUID.randomUUID().toString().replace("-", "");
        long expirationSeconds = 86400; // 24 horas
        long expiresAtEpochMs = Instant.now().toEpochMilli() + (expirationSeconds * 1000);

        activeTokens.put(generatedToken, new AccessGrant(expiresAtEpochMs, false, ""));

        return new LoginResponse(
                generatedToken,
                "Bearer",
                expirationSeconds,
                user
        );
    }

    @Override
    public ServiceTokenResponse issueServiceToken(String grantType,
                                                  ClientCredentialsRequest request,
                                                  String requestedScope) {
        if (!"client_credentials".equals(grantType)) {
            throw new OAuthTokenException("unsupported_grant_type", 400);
        }
        if (request == null || request.clientId() == null || request.clientSecret() == null
                || pythonClientId.isBlank() || pythonClientSecret.isBlank()
                || !pythonClientId.equals(request.clientId())
                || !MessageDigest.isEqual(
                        pythonClientSecret.getBytes(StandardCharsets.UTF_8),
                        request.clientSecret().getBytes(StandardCharsets.UTF_8))) {
            throw new OAuthTokenException("invalid_client", 401);
        }

        String scope = requestedScope == null || requestedScope.isBlank()
                ? ANALYTICS_READ_SCOPE
                : requestedScope.trim();
        if (!ANALYTICS_READ_SCOPE.equals(scope)) {
            throw new OAuthTokenException("invalid_scope", 400);
        }

        long now = Instant.now().toEpochMilli();
        activeTokens.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMs() <= now);
        String accessToken = "svc_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        long expiresAtEpochMs = now + TOKEN_LIFETIME_SECONDS * 1000;
        activeTokens.put(accessToken, new AccessGrant(expiresAtEpochMs, true, scope));

        return new ServiceTokenResponse(accessToken, "Bearer", TOKEN_LIFETIME_SECONDS, scope);
    }

    @Override
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        AccessGrant grant = activeTokens.get(token);
        if (grant != null && grant.expiresAtEpochMs() <= Instant.now().toEpochMilli()) {
            activeTokens.remove(token, grant);
            return false;
        }

        return grant != null;
    }

    @Override
    public boolean isAuthorizedForRequest(String token, String method, String path) {
        AccessGrant grant = activeTokens.get(token);
        if (grant == null || !validateToken(token)) {
            return false;
        }
        if (!grant.serviceClient()) {
            return true;
        }
        return ANALYTICS_READ_SCOPE.equals(grant.scope())
                && "GET".equalsIgnoreCase(method)
                && path.matches(HISTORY_PATH_PATTERN);
    }

    private record AccessGrant(long expiresAtEpochMs, boolean serviceClient, String scope) {}
}

