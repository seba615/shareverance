package com.shareverance.dto;

import com.shareverance.model.User;

public record LoginResponse(
    long expiresIn,
    User user
) {}
