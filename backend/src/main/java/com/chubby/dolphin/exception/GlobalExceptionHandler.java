package com.chubby.dolphin.exception;

import lombok.extern.slf4j.Slf4j;
import com.chubby.dolphin.security.TenantAccessService;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    /** Silently return 404 — don't spam logs for missing static resources */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not found", "path", ex.getResourcePath()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Bad request", "message", safeMessage(ex.getMessage(), "Invalid request.")));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<?> handleEntityNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found", "message", safeMessage(ex.getMessage(), "Resource not found.")));
    }

    @ExceptionHandler(TenantAccessService.TenantAccessDeniedException.class)
    public ResponseEntity<?> handleTenantAccessDenied(TenantAccessService.TenantAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", safeMessage(ex.getMessage(), "Workspace access denied.")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAll(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "error",     "Internal server error",
            "message",   safeMessage(ex.getMessage(), "Something went wrong. Please try again."),
            "timestamp", LocalDateTime.now().toString()
        ));
    }

    private String safeMessage(String detail, String fallback) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        return prod ? fallback : detail;
    }
}
