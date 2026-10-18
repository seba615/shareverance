package com.shareverance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.shareverance.exception.GlobalExceptionHandler;

class GlobalExceptionHandlerTest {

    @Test
    void preservesAuthorizationStatusInsteadOfConvertingItToServerError() {
        var response = new GlobalExceptionHandler().handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere rol ADMIN"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Se requiere rol ADMIN", response.getBody().get("message"));
    }
}
