package com.shareverance.dto;

import com.shareverance.model.User;

public record LoginResponse(
    String token,
    String tokenType,
    long expiresIn,
    User user
) {}

