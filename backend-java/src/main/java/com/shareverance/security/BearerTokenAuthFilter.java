package com.shareverance.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shareverance.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BearerTokenAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public BearerTokenAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 1. Endpoints públicos: probes, actuator y endpoint de login
        if (path.startsWith("/actuator") || path.equals("/health")
            || path.equals("/api/v1/dev/status")
            || path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/token")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Permitir preflight OPTIONS requests para CORS
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Validar encabezado Authorization
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorizedResponse(response, "Falta el encabezado Authorization con formato Bearer <token>");
            return;
        }

        String token = authHeader.substring(7).trim();

        if (!authService.validateToken(token)) {
            sendUnauthorizedResponse(response, "Token de autenticación inválido o expirado");
            return;
        }

        if (!authService.isAuthorizedForRequest(token, request.getMethod(), path)) {
            sendForbiddenResponse(response, "El token no tiene permisos para este recurso");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String json = String.format("{\"error\": \"Unauthorized\", \"message\": \"%s\", \"status\": 401}", message);
        response.getWriter().write(json);
    }

    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String json = String.format("{\"error\": \"Forbidden\", \"message\": \"%s\", \"status\": 403}", message);
        response.getWriter().write(json);
    }
}

