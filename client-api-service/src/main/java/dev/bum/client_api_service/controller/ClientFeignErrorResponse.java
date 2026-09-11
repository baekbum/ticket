package dev.bum.client_api_service.controller;

import feign.FeignException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ClientFeignErrorResponse {

    private ClientFeignErrorResponse() {
    }

    public static ResponseEntity<String> from(FeignException exception) {
        return ResponseEntity
                .status(HttpStatusCode.valueOf(exception.status()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(exception.contentUTF8());
    }
}
