package com.shareverance.controller;

import com.shareverance.dto.LoginRequest;
import com.shareverance.dto.LoginResponse;
import com.shareverance.dto.ClientCredentialsRequest;
import com.shareverance.dto.ServiceTokenResponse;
import com.shareverance.exception.OAuthTokenException;
import com.shareverance.service.AuthService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ServiceTokenResponse> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "scope", required = false) String scope) {
        ServiceTokenResponse token = authService.issueServiceToken(
                grantType,
                new ClientCredentialsRequest(clientId, clientSecret),
            scope);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("Pragma", "no-cache")
            .body(token);
    }

    @ExceptionHandler(OAuthTokenException.class)
    public ResponseEntity<Map<String, String>> handleOAuthTokenException(OAuthTokenException exception) {
        return ResponseEntity.status(HttpStatusCode.valueOf(exception.getStatus()))
            .cacheControl(CacheControl.noStore())
            .header("Pragma", "no-cache")
                .body(Map.of("error", exception.getError()));
    }
}

