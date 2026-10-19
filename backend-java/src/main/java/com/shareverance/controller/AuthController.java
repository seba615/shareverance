package com.shareverance.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shareverance.dto.LoginRequest;
import com.shareverance.dto.LoginResponse;
import com.shareverance.dto.UserRequest;
import com.shareverance.model.User;
import com.shareverance.service.AuthService;
import com.shareverance.service.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String SESSION_COOKIE = "SV_SESSION";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final long USER_SESSION_LIFETIME_SECONDS = 86400;

    private final AuthService authService;
    private final UserService userService;
    private final boolean secureCookies;

    @Autowired
    public AuthController(AuthService authService, UserService userService,
                          @Value("${security.cookies.secure:false}") boolean secureCookies) {
        this.authService = authService;
        this.userService = userService;
        this.secureCookies = secureCookies;
    }

    public AuthController(AuthService authService) {
        this(authService, null, false);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrfToken() {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE, token)
                .path("/")
                .httpOnly(false)
                .secure(secureCookies)
                .sameSite("Lax")
                .maxAge(USER_SESSION_LIFETIME_SECONDS)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(Map.of("csrfToken", token));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return sessionResponse(authService.login(request), 200);
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@RequestBody UserRequest request) {
        if (userService == null) {
            throw new IllegalStateException("User registration is not configured");
        }
        User created = userService.createUser(request);
        return sessionResponse(authService.login(new LoginRequest(created.getEmail(), request.password())), 201);
    }

    @GetMapping("/me")
    public ResponseEntity<User> currentUser(HttpServletRequest request) {
        return ResponseEntity.ok(authService.getAuthenticatedUser(sessionToken(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(sessionToken(request));
        ResponseCookie sessionCookie = clearCookie(SESSION_COOKIE, true);
        ResponseCookie csrfCookie = clearCookie(CSRF_COOKIE, false);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie.toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private ResponseEntity<LoginResponse> sessionResponse(AuthService.LoginResult result, int status) {
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, result.token())
                .path("/")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .maxAge(result.expiresIn())
                .build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(new LoginResponse(result.expiresIn(), result.user()));
    }

    private ResponseCookie clearCookie(String name, boolean httpOnly) {
        return ResponseCookie.from(name, "")
                .path("/")
                .httpOnly(httpOnly)
                .secure(secureCookies)
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }

    private String sessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
