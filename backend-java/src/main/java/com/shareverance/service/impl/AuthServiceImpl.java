package com.shareverance.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.shareverance.dto.LoginRequest;
import com.shareverance.exception.BusinessRuleException;
import com.shareverance.model.Category;
import com.shareverance.model.Expense;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.Organization;
import com.shareverance.model.User;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.CategoryRepository;
import com.shareverance.repository.ExpenseRepository;
import com.shareverance.repository.OrganizationRepository;
import com.shareverance.repository.UserOrganizationRepository;
import com.shareverance.repository.SessionRepository;
import com.shareverance.repository.UserRepository;
import com.shareverance.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserOrganizationRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final SessionRepository sessionRepository;
    private final Map<String, AccessGrant> activeTokens = new ConcurrentHashMap<>();

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           UserOrganizationRepository membershipRepository,
                           OrganizationRepository organizationRepository,
                           ExpenseRepository expenseRepository,
                           CategoryRepository categoryRepository,
                           SessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.sessionRepository = sessionRepository;
    }

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           UserOrganizationRepository membershipRepository,
                           OrganizationRepository organizationRepository,
                           ExpenseRepository expenseRepository,
                           CategoryRepository categoryRepository,
                           SessionRepository sessionRepository,
                           String ignoredClientId,
                           String ignoredClientSecret) {
        this(userRepository, passwordEncoder, membershipRepository, organizationRepository,
                expenseRepository, categoryRepository, sessionRepository);
    }

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           String ignoredClientId,
                           String ignoredClientSecret) {
        this(userRepository, passwordEncoder, null, null, null, null, null);
    }

    @Override
    public LoginResult login(LoginRequest request) {
        if (request == null) {
            throw new BusinessRuleException("El correo electrónico y la contraseña son requeridos");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessRuleException("El correo electrónico es requerido");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessRuleException("La contraseña es requerida");
        }

        User user = userRepository.findByEmail(request.email().trim())
                .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("La cuenta de usuario está desactivada");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessRuleException("Credenciales inválidas");
        }

        // Generar un token aleatorio seguro
        String generatedToken = "usr_" + UUID.randomUUID().toString().replace("-", "");
        long expirationSeconds = 86400; // 24 horas
        long expiresAtEpochMs = Instant.now().toEpochMilli() + (expirationSeconds * 1000);

        if (sessionRepository != null) {
            Instant expiresAt = Instant.ofEpochMilli(expiresAtEpochMs);
            sessionRepository.deleteExpired(Instant.now());
            sessionRepository.save(hashToken(generatedToken), user.getId(), expiresAt);
        }
        activeTokens.put(generatedToken, new AccessGrant(expiresAtEpochMs, user.getId()));

        return new LoginResult(
                generatedToken,
                expirationSeconds,
                user
        );
    }

    @Override
    public User getAuthenticatedUser(String token) {
        Long userId = getAuthenticatedUserId(token);
        if (userId == null) {
            throw new BusinessRuleException("La sesión no es válida");
        }
        return userRepository.findById(userId)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new BusinessRuleException("La cuenta de usuario está desactivada"));
    }

    @Override
    public Long getAuthenticatedUserId(String token) {
        AccessGrant grant = activeTokens.get(token);
        Long userId;
        if (grant != null) {
            userId = !validateToken(token) ? null : grant.userId();
        } else if (sessionRepository == null || token == null || token.isBlank()) {
            return null;
        } else {
            userId = sessionRepository.findActiveUserId(hashToken(token), Instant.now()).orElse(null);
        }
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .map(User::getId)
                .orElse(null);
    }

    @Override
    public void logout(String token) {
        if (token != null) {
            activeTokens.remove(token);
            if (sessionRepository != null) {
                sessionRepository.delete(hashToken(token));
            }
        }
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

        if (grant != null) {
            return sessionRepository == null
                    || sessionRepository.findActiveUserId(hashToken(token), Instant.now()).isPresent();
        }
        return sessionRepository != null
                && sessionRepository.findActiveUserId(hashToken(token), Instant.now()).isPresent();
    }

    @Override
    public boolean isAuthorizedForRequest(String token, String method, String path) {
        AccessGrant grant = activeTokens.get(token);
        if (grant == null) {
            Long userId = getAuthenticatedUserId(token);
            return userId != null && authorizeUserRequest(userId, method, path);
        }
        if (!validateToken(token)) {
            return false;
        }
        return authorizeUserRequest(grant.userId(), method, path);
    }

    @Override
    public boolean isOrganizationMember(Long userId, Long organizationId) {
        return activeMembership(userId, organizationId).isPresent();
    }

    @Override
    public boolean isOrganizationAdmin(Long userId, Long organizationId) {
        return activeMembership(userId, organizationId)
                .map(UserOrganization::getRole)
                .filter(MembershipRole.ADMIN::equals)
                .isPresent();
    }

    @Override
    public boolean canModifyExpense(Long userId, Long expenseId) {
        if (expenseRepository == null || userId == null || expenseId == null) {
            return false;
        }
        return expenseRepository.findById(expenseId)
                .filter(expense -> Boolean.TRUE.equals(expense.getIsActive()))
                .filter(expense -> isOrganizationMember(userId, expense.getOrganizationId()))
                .filter(expense -> Objects.equals(expense.getCreatedByUserId(), userId)
                        || isOrganizationAdmin(userId, expense.getOrganizationId()))
                .isPresent();
    }

    private Optional<UserOrganization> activeMembership(Long userId, Long organizationId) {
        if (membershipRepository == null || userId == null || organizationId == null
                || userRepository.findById(userId).filter(user -> Boolean.TRUE.equals(user.getIsActive())).isEmpty()
                || !hasActiveArsOrganization(organizationId)) {
            return Optional.empty();
        }
        return membershipRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(membership -> Boolean.TRUE.equals(membership.getIsActive()));
    }

    private boolean hasActiveArsOrganization(Long organizationId) {
        return organizationRepository != null && organizationRepository.findById(organizationId)
                .filter(organization -> Boolean.TRUE.equals(organization.getIsActive()))
                .map(Organization::getCurrency)
                .filter(currency -> "ARS".equalsIgnoreCase(currency))
                .isPresent();
    }

    private boolean authorizeUserRequest(Long userId, String method, String path) {
        if (path.equals("/api/v1/auth/me") || path.equals("/api/v1/auth/logout")) {
            return true;
        }
        if (path.equals("/api/v1/users") || path.startsWith("/api/v1/users/")) {
            return "GET".equalsIgnoreCase(method) && path.matches("/api/v1/users/\\d+")
                    && path.endsWith("/" + userId);
        }
        if (path.equals("/api/v1/organizations")) {
            return "GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method);
        }
        if (path.matches("/api/v1/organizations/\\d+/analytics/coverage(/[^/]+)?")) {
            Long organizationId = Long.parseLong(path.split("/")[4]);
            boolean collection = path.endsWith("/coverage");
            return isOrganizationAdmin(userId, organizationId) &&
                    (collection ? "GET".equalsIgnoreCase(method) :
                            "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method));
        }
        if (path.matches("/api/v1/organizations/\\d+/(predictions|analytics)/monthly")) {
            Long organizationId = Long.parseLong(path.split("/")[4]);
            return "GET".equalsIgnoreCase(method) && isOrganizationMember(userId, organizationId);
        }
        if (path.matches("/api/v1/organizations/\\d+/receipts/[^/]+/ocr")) {
            Long organizationId = Long.parseLong(path.split("/")[4]);
            return "POST".equalsIgnoreCase(method) && isOrganizationMember(userId, organizationId);
        }
        if (path.matches("/api/v1/organizations/\\d+/receipts(/[^/]+)?(/metadata)?")) {
            Long organizationId = Long.parseLong(path.split("/")[4]);
            if (!isOrganizationMember(userId, organizationId)) {
                return false;
            }
            if ("POST".equalsIgnoreCase(method)) {
                return path.endsWith("/receipts");
            }
            return "GET".equalsIgnoreCase(method);
        }
        if (path.matches("/api/v1/organizations/\\d+")) {
            Long organizationId = lastId(path);
            if ("GET".equalsIgnoreCase(method)) {
                return isOrganizationMember(userId, organizationId);
            }
            return ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))
                    && isOrganizationAdmin(userId, organizationId);
        }
        if (path.startsWith("/api/v1/categories/")) {
            if (path.matches("/api/v1/categories/organization/\\d+")) {
                Long organizationId = lastId(path);
                if ("GET".equalsIgnoreCase(method)) {
                    return isOrganizationMember(userId, organizationId);
                }
                return "POST".equalsIgnoreCase(method) && isOrganizationAdmin(userId, organizationId);
            }
            if (path.matches("/api/v1/categories/\\d+")) {
                Long categoryId = lastId(path);
                if (categoryRepository == null) {
                    return false;
                }
                return categoryRepository.findById(categoryId)
                        .map(Category::getOrganizationId)
                        .map(organizationId -> {
                            if ("GET".equalsIgnoreCase(method)) {
                                return isOrganizationMember(userId, organizationId);
                            }
                            return ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))
                                    && isOrganizationAdmin(userId, organizationId);
                        })
                        .orElse(false);
            }
        }
        if (path.equals("/api/v1/expenses")) {
            return "POST".equalsIgnoreCase(method);
        }
        if (path.matches("/api/v1/expenses/organization/\\d+")) {
            return "GET".equalsIgnoreCase(method) && isOrganizationMember(userId, lastId(path));
        }
        if (path.matches("/api/v1/expenses/organization/\\d+/(page|calendar)")) {
            String[] pathParts = path.split("/");
            Long organizationId = Long.parseLong(pathParts[pathParts.length - 2]);
            return "GET".equalsIgnoreCase(method) && isOrganizationMember(userId, organizationId);
        }
        if (path.matches("/api/v1/expenses/\\d+")) {
            Long expenseId = lastId(path);
            if ("GET".equalsIgnoreCase(method)) {
                return expenseRepository != null && expenseRepository.findById(expenseId)
                        .map(Expense::getOrganizationId)
                        .map(organizationId -> isOrganizationMember(userId, organizationId))
                        .orElse(false);
            }
            return ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))
                    && canModifyExpense(userId, expenseId);
        }
        if (path.startsWith("/api/v1/memberships/")) {
            if (path.matches("/api/v1/memberships/organization/\\d+")) {
                return "GET".equalsIgnoreCase(method)
                        && isOrganizationMember(userId, lastId(path));
            }
            if (path.matches("/api/v1/memberships/user/\\d+")) {
                return "GET".equalsIgnoreCase(method) && lastId(path).equals(userId);
            }
            if (path.matches("/api/v1/memberships/\\d+")) {
                if (membershipRepository == null) {
                    return false;
                }
                return membershipRepository.findById(lastId(path))
                        .map(membership -> {
                            if ("GET".equalsIgnoreCase(method)) {
                                return isOrganizationAdmin(userId, membership.getOrganizationId())
                                        || (Objects.equals(membership.getUserId(), userId)
                                                && Boolean.TRUE.equals(membership.getIsActive())
                                                && isOrganizationMember(userId, membership.getOrganizationId()));
                            }
                            return "DELETE".equalsIgnoreCase(method)
                                    && isOrganizationAdmin(userId, membership.getOrganizationId());
                        })
                        .orElse(false);
            }
            if (path.matches("/api/v1/memberships/\\d+/role") || path.matches("/api/v1/memberships/\\d+/reactivate")) {
                Long membershipId = Long.parseLong(path.replaceAll(".*/(\\d+)/(role|reactivate)", "$1"));
                return "PATCH".equalsIgnoreCase(method) && membershipRepository != null
                        && membershipRepository.findById(membershipId)
                        .map(membership -> isOrganizationAdmin(userId, membership.getOrganizationId()))
                        .orElse(false);
            }
            if (path.equals("/api/v1/memberships")) {
                return false;
            }
        }
        if (path.equals("/api/v1/memberships")) {
            return "POST".equalsIgnoreCase(method) && hasAdminMembership(userId);
        }
        return false;
    }

    private boolean hasAdminMembership(Long userId) {
        return membershipRepository != null && membershipRepository.findByUserId(userId).stream()
                .anyMatch(membership -> Boolean.TRUE.equals(membership.getIsActive())
                        && MembershipRole.ADMIN.equals(membership.getRole())
                        && isOrganizationAdmin(userId, membership.getOrganizationId()));
    }

    private static Long lastId(String path) {
        String[] parts = path.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record AccessGrant(long expiresAtEpochMs, Long userId) {}
}
