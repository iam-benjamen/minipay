package com.minipay.common.exception;

import org.springframework.http.HttpStatus;

public abstract class MiniPayException extends RuntimeException {
    private final HttpStatus status;

    protected MiniPayException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}