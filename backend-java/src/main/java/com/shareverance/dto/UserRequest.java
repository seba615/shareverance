package com.shareverance.dto;

public record UserRequest(
    String fullName,
    String email,
    String password
) {}

