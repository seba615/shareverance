package com.shareverance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.shareverance.controller.AuthController;
import com.shareverance.dto.ClientCredentialsRequest;
import com.shareverance.dto.LoginRequest;
import com.shareverance.dto.UserRequest;
import com.shareverance.exception.OAuthTokenException;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.model.User;
import com.shareverance.model.Expense;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.UserRepository;
import com.shareverance.repository.UserOrganizationRepository;
import com.shareverance.repository.ExpenseRepository;
import com.shareverance.repository.CategoryRepository;
import com.shareverance.repository.SessionRepository;
import com.shareverance.service.impl.AuthServiceImpl;
import com.shareverance.service.UserService;

import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.Instant;

class AuthServiceTest {

    private AuthServiceImpl authService;
    private UserRepository userRepository;
    private UserOrganizationRepository membershipRepository;
    private ExpenseRepository expenseRepository;
    private CategoryRepository categoryRepository;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        membershipRepository = mock(UserOrganizationRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthServiceImpl(userRepository, passwordEncoder,
                membershipRepository, expenseRepository, categoryRepository, null,
                "python-client", "test-secret");
    }

    @Test
    void acceptsLoginWithMatchingPassword() {
        User user = new User(1L, "Admin", "admin@shareverance.com",
                passwordEncoder.encode("password123"), null, true);
        when(userRepository.findByEmail("admin@shareverance.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = authService.login(new LoginRequest("admin@shareverance.com", "password123"));

        assertEquals(86400, response.expiresIn());
        assertTrue(authService.validateToken(response.token()));
        assertEquals(1L, authService.getAuthenticatedUserId(response.token()));
    }

    @Test
    void devAdminSeedHashMatchesPassword123() {
        assertTrue(passwordEncoder.matches(
                "password123",
                "$2a$10$xPEyIRY8G7tNH9viFwPnuuwLmdzdAX/5xpDhQGqvmHplpMA4XD2.m"));
    }

    @Test
    void userSerializationDoesNotExposePasswordHash() throws Exception {
        var user = new User(1L, "Admin", "admin@shareverance.com", "secret-hash", null, true);

        String json = new ObjectMapper().writeValueAsString(user);

        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("secret-hash"));
    }

    @Test
    void rejectsLoginWithIncorrectPassword() {
        when(userRepository.findByEmail("admin@shareverance.com")).thenReturn(Optional.of(
                new User(1L, "Admin", "admin@shareverance.com",
                        passwordEncoder.encode("password123"), null, true)));

        var exception = assertThrows(BusinessRuleException.class,
                () -> authService.login(new LoginRequest("admin@shareverance.com", "incorrect")));

        assertEquals("Credenciales inválidas", exception.getMessage());
    }

    @Test
    void logoutInvalidatesUserSession() {
        when(userRepository.findByEmail("admin@shareverance.com")).thenReturn(Optional.of(
                new User(1L, "Admin", "admin@shareverance.com",
                        passwordEncoder.encode("password123"), null, true)));
        var session = authService.login(new LoginRequest("admin@shareverance.com", "password123"));

        authService.logout(session.token());

        assertFalse(authService.validateToken(session.token()));
    }

    @Test
    void userAuthorizationIsLimitedToActiveOrganizationMemberships() {
        User user = new User(1L, "User", "user@shareverance.com",
                passwordEncoder.encode("password123"), null, true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndOrganizationId(1L, 10L)).thenReturn(Optional.of(
                new UserOrganization(1L, 1L, 10L, MembershipRole.USER, LocalDateTime.now(), true)));
        when(membershipRepository.findByUserIdAndOrganizationId(1L, 20L)).thenReturn(Optional.empty());
        var session = authService.login(new LoginRequest(user.getEmail(), "password123"));

        assertTrue(authService.isAuthorizedForRequest(
                session.token(), "GET", "/api/v1/organizations/10"));
        assertFalse(authService.isAuthorizedForRequest(
                session.token(), "GET", "/api/v1/organizations/20"));
    }

    @Test
    void regularUsersMayModifyOnlyTheirOwnExpenses() {
        User user = new User(1L, "User", "user@shareverance.com",
                passwordEncoder.encode("password123"), null, true);
        Expense expense = new Expense(5L, 10L, 2L, 1L, 3L, new BigDecimal("20.00"),
                LocalDate.now(), "Item", null, LocalDateTime.now(), true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(expenseRepository.findById(5L)).thenReturn(Optional.of(expense));
        when(membershipRepository.findByUserIdAndOrganizationId(1L, 10L)).thenReturn(Optional.of(
                new UserOrganization(1L, 1L, 10L, MembershipRole.USER, LocalDateTime.now(), true)));
        var session = authService.login(new LoginRequest(user.getEmail(), "password123"));

        assertFalse(authService.isAuthorizedForRequest(
                session.token(), "PUT", "/api/v1/expenses/5"));

        expense.setCreatedByUserId(1L);
        assertTrue(authService.isAuthorizedForRequest(
                session.token(), "PUT", "/api/v1/expenses/5"));

        expense.setCreatedByUserId(2L);
        when(membershipRepository.findByUserIdAndOrganizationId(1L, 10L)).thenReturn(Optional.of(
                new UserOrganization(1L, 1L, 10L, MembershipRole.ADMIN, LocalDateTime.now(), true)));
        assertTrue(authService.isAuthorizedForRequest(
                session.token(), "PUT", "/api/v1/expenses/5"));
    }

    @Test
    void userSessionsPersistOnlyTokenHashesAndSurviveServiceRestart() {
        User user = new User(1L, "User", "user@shareverance.com",
                passwordEncoder.encode("password123"), null, true);
        SessionRepository sessions = mock(SessionRepository.class);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sessions.findActiveUserId(anyString(), any(Instant.class))).thenReturn(Optional.of(1L));
        AuthServiceImpl service = new AuthServiceImpl(userRepository, passwordEncoder,
                membershipRepository, expenseRepository, categoryRepository, sessions,
                "python-client", "test-secret");
        var session = service.login(new LoginRequest(user.getEmail(), "password123"));

        var hashCaptor = forClass(String.class);
        verify(sessions).save(hashCaptor.capture(), eq(1L), any(Instant.class));
        assertEquals(64, hashCaptor.getValue().length());
        assertFalse(hashCaptor.getValue().equals(session.token()));

        AuthServiceImpl restartedService = new AuthServiceImpl(userRepository, passwordEncoder,
                membershipRepository, expenseRepository, categoryRepository, sessions,
                "python-client", "test-secret");
        assertTrue(restartedService.validateToken(session.token()));
        assertEquals(1L, restartedService.getAuthenticatedUserId(session.token()));

        when(sessions.findActiveUserId(anyString(), any(Instant.class))).thenReturn(Optional.empty());
        assertFalse(restartedService.validateToken(session.token()));
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

    @Test
    void loginReturnsHttpOnlyCookieWithoutExposingSessionToken() throws Exception {
        User user = new User(1L, "Admin", "admin@shareverance.com",
                passwordEncoder.encode("password123"), null, true);
        when(userRepository.findByEmail("admin@shareverance.com")).thenReturn(Optional.of(user));
        MockMvc mockMvc = standaloneSetup(new AuthController(authService)).build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(
                                new LoginRequest("admin@shareverance.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SV_SESSION=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.user.email").value("admin@shareverance.com"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void registrationCreatesUserAndStartsSessionImmediately() throws Exception {
        String email = "new-user@shareverance.com";
        User user = new User(2L, "New User", email, passwordEncoder.encode("password123"), null, true);
        UserService userService = mock(UserService.class);
        when(userService.createUser(new UserRequest("New User", email, "password123"))).thenReturn(user);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        MockMvc mockMvc = standaloneSetup(new AuthController(authService, userService, false)).build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(
                                new UserRequest("New User", email, "password123"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.token").doesNotExist());
    }
}