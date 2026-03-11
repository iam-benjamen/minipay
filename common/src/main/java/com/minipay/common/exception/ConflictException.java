package com.minipay.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends MiniPayException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}