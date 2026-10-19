package com.shareverance.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.shareverance.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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

        if (isUnsafeMethod(request.getMethod()) && hasCookie(request, "SV_SESSION")
                && !validCsrf(request)) {
            sendForbiddenResponse(response, "Token CSRF faltante o inválido");
            return;
        }

        if (path.startsWith("/actuator") || path.equals("/health")
                || path.equals("/api/v1/dev/status")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/csrf")
                || path.equals("/api/v1/auth/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = bearerToken(request);
        if (token == null) {
            token = cookieValue(request, "SV_SESSION");
        }
        if (token == null || token.isBlank()) {
            sendUnauthorizedResponse(response, "Falta una sesión válida");
            return;
        }

        if (!authService.validateToken(token)) {
            sendUnauthorizedResponse(response, "Token de autenticación inválido o expirado");
            return;
        }

        if (!authService.isAuthorizedForRequest(token, request.getMethod(), path)) {
            sendForbiddenResponse(response, "El token no tiene permisos para este recurso");
            return;
        }

        Long userId = authService.getAuthenticatedUserId(token);
        if (userId != null) {
            request.setAttribute("authenticatedUserId", userId);
        }
        filterChain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7).trim()
                : null;
    }

    private static boolean isUnsafeMethod(String method) {
        return !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method);
    }

    private static boolean validCsrf(HttpServletRequest request) {
        String cookieToken = cookieValue(request, "XSRF-TOKEN");
        String headerToken = request.getHeader("X-XSRF-TOKEN");
        return cookieToken != null && headerToken != null
                && MessageDigest.isEqual(cookieToken.getBytes(StandardCharsets.UTF_8),
                        headerToken.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasCookie(HttpServletRequest request, String name) {
        return cookieValue(request, name) != null;
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"status\":401}", message));
    }

    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"error\":\"Forbidden\",\"message\":\"%s\",\"status\":403}", message));
    }
}
