package com.shareverance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.shareverance.security.BearerTokenAuthFilter;
import com.shareverance.service.AuthService;

import jakarta.servlet.FilterChain;

class BearerTokenAuthFilterTest {

    @Test
    void allowsDevelopmentStatusWithoutBearerToken() throws Exception {
        AuthService authService = mock(AuthService.class);
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(authService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dev/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(authService);
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsCookieAuthenticatedMutationWithoutCsrfToken() throws Exception {
        AuthService authService = mock(AuthService.class);
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(authService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/organizations");
        request.setCookies(new jakarta.servlet.http.Cookie("SV_SESSION", "valid-session"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(authService);
    }

    @Test
    void acceptsAuthenticatedSessionCookieForAuthorizedRead() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.validateToken("valid-session")).thenReturn(true);
        when(authService.isAuthorizedForRequest("valid-session", "GET", "/api/v1/auth/me")).thenReturn(true);
        when(authService.getAuthenticatedUserId("valid-session")).thenReturn(7L);
        BearerTokenAuthFilter filter = new BearerTokenAuthFilter(authService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setCookies(new jakarta.servlet.http.Cookie("SV_SESSION", "valid-session"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(7L, request.getAttribute("authenticatedUserId"));
        assertEquals(200, response.getStatus());
    }
}