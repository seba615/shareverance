package com.shareverance;

import com.shareverance.dto.ClientCredentialsRequest;
import com.shareverance.controller.AuthController;
import com.shareverance.exception.OAuthTokenException;
import com.shareverance.repository.UserRepository;
import com.shareverance.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthServiceTest {

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(mock(UserRepository.class), "python-client", "test-secret");
    }

    @Test
    void issuesScopedClientCredentialsToken() {
        var response = authService.issueServiceToken(
                "client_credentials",
                new ClientCredentialsRequest("python-client", "test-secret"),
                "analytics:read");

        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresIn());
        assertEquals("analytics:read", response.scope());
        assertTrue(authService.validateToken(response.accessToken()));
        assertTrue(authService.isAuthorizedForRequest(
                response.accessToken(), "GET", "/api/v1/expenses/organization/1/history"));
        assertFalse(authService.isAuthorizedForRequest(
                response.accessToken(), "GET", "/api/v1/expenses/organization/1"));
        assertFalse(authService.isAuthorizedForRequest(
                response.accessToken(), "POST", "/api/v1/expenses/organization/1/history"));
    }

    @Test
    void rejectsInvalidClientCredentials() {
        var exception = assertThrows(OAuthTokenException.class, () -> authService.issueServiceToken(
                "client_credentials",
                new ClientCredentialsRequest("python-client", "wrong-secret"),
                null));

        assertEquals("invalid_client", exception.getError());
        assertEquals(401, exception.getStatus());
    }

    @Test
    void rejectsUnsupportedGrantAndScope() {
        var grantException = assertThrows(OAuthTokenException.class, () -> authService.issueServiceToken(
                "password",
                new ClientCredentialsRequest("python-client", "test-secret"),
                null));
        assertEquals("unsupported_grant_type", grantException.getError());

        var scopeException = assertThrows(OAuthTokenException.class, () -> authService.issueServiceToken(
                "client_credentials",
                new ClientCredentialsRequest("python-client", "test-secret"),
                "expenses:write"));
        assertEquals("invalid_scope", scopeException.getError());
    }

        @Test
        void oauthTokenEndpointAcceptsFormAndReturnsStandardFields() throws Exception {
                MockMvc mockMvc = standaloneSetup(new AuthController(authService)).build();

                mockMvc.perform(post("/api/v1/auth/token")
                                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                .param("grant_type", "client_credentials")
                                                .param("client_id", "python-client")
                                                .param("client_secret", "test-secret")
                                                .param("scope", "analytics:read"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.access_token").isNotEmpty())
                                .andExpect(jsonPath("$.token_type").value("Bearer"))
                                .andExpect(jsonPath("$.expires_in").value(3600))
                                .andExpect(jsonPath("$.scope").value("analytics:read"));
        }
}