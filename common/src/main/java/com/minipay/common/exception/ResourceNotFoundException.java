package com.minipay.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends MiniPayException {

    public ResourceNotFoundException(String resource, String identifier) {
        super(resource + " not found: " + identifier, HttpStatus.NOT_FOUND);
    }
}