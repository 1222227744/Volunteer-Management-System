package com.volunteer.vms.common;

import org.springframework.http.HttpStatus;

public class BizException extends RuntimeException {
    private final HttpStatus status;

    public BizException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
