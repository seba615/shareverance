package com.shareverance;

import com.shareverance.security.BearerTokenAuthFilter;
import com.shareverance.service.AuthService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
}