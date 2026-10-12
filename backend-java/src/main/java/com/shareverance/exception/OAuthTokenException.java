package com.shareverance.exception;

public class OAuthTokenException extends RuntimeException {
    private final String error;
    private final int status;

    public OAuthTokenException(String error, int status) {
        super(error);
        this.error = error;
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public int getStatus() {
        return status;
    }
}