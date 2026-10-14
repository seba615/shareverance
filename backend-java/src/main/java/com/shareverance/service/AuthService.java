package com.shareverance.service;

import com.shareverance.dto.ClientCredentialsRequest;
import com.shareverance.dto.LoginRequest;
import com.shareverance.dto.ServiceTokenResponse;
import com.shareverance.model.User;

public interface AuthService {
    LoginResult login(LoginRequest request);
    User getAuthenticatedUser(String token);
    Long getAuthenticatedUserId(String token);
    void logout(String token);
    ServiceTokenResponse issueServiceToken(String grantType, ClientCredentialsRequest request, String scope);
    boolean validateToken(String token);
    boolean isAuthorizedForRequest(String token, String method, String path);
    boolean isOrganizationMember(Long userId, Long organizationId);
    boolean isOrganizationAdmin(Long userId, Long organizationId);
    boolean canModifyExpense(Long userId, Long expenseId);

    record LoginResult(String token, long expiresIn, User user) {}
}
