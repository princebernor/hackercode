package dev.hackercode.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every API error leaves the server as {@code {"error": {"code", "message", "fields"?}}}. Stack traces never do. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static Map<String, Object> body(String code, String message, Map<String, String> fields) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        if (fields != null && !fields.isEmpty()) err.put("fields", fields);
        return Map.of("error", err);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(body(e.code(), e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Object> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(body("validation_failed", "Please correct the highlighted fields.", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Object> unreadable(Exception e) {
        return ResponseEntity.badRequest().body(body("bad_request", "The request could not be read.", null));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<Object> denied(Exception e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("forbidden", "You do not have permission to do that.", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Object> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body("method_not_allowed", "Method not allowed.", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Object> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("not_found", "Not found.", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("internal_error", "Something went wrong on our side. Please try again.", null));
    }
}
