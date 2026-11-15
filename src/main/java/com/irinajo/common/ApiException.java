package com.irinajo.common;

import org.springframework.http.HttpStatus;

/**
 * Error with a stable machine-readable code, so the app knows whether to retry.
 */
public class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final boolean retryable;
    private final Integer retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message, boolean retryable, Integer retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, false, null);
    }

    public static ApiException badRequest(String code, String msg) { return new ApiException(HttpStatus.BAD_REQUEST, code, msg); }
    public static ApiException unauthorized(String code, String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, code, msg); }
    public static ApiException forbidden(String code, String msg) { return new ApiException(HttpStatus.FORBIDDEN, code, msg); }
    public static ApiException notFound(String code, String msg) { return new ApiException(HttpStatus.NOT_FOUND, code, msg); }
    public static ApiException conflict(String code, String msg) { return new ApiException(HttpStatus.CONFLICT, code, msg); }
    public static ApiException tooMany(String code, String msg, int retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, msg, true, retryAfter);
    }
    public static ApiException upstream(String code, String msg) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, msg, true, 5);
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public boolean isRetryable() { return retryable; }
    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
}
