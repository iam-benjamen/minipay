package com.minipay.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends MiniPayException {
    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}