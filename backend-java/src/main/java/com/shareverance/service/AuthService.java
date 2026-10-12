package com.shareverance.service;

import com.shareverance.dto.ClientCredentialsRequest;
import com.shareverance.dto.LoginRequest;
import com.shareverance.dto.LoginResponse;
import com.shareverance.dto.ServiceTokenResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    ServiceTokenResponse issueServiceToken(String grantType, ClientCredentialsRequest request, String scope);
    boolean validateToken(String token);
    boolean isAuthorizedForRequest(String token, String method, String path);
}

