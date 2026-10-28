package dev.hackercode.common;

import org.springframework.http.HttpStatus;

/** An error that is safe to show to the client: it carries a status, a stable code and a human message. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    public static ApiException notFound(String what) { return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found"); }
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, "forbidden", message); }
    public static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    public static ApiException unavailable(String code, String message) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message); }
    public static ApiException tooMany(String message) { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", message); }
}
