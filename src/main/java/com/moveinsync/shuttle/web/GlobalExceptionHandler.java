package com.moveinsync.shuttle.web;

import com.moveinsync.shuttle.exception.ApiException;
import com.moveinsync.shuttle.exception.ErrorResponse;
import com.moveinsync.shuttle.exception.ErrorResponse.FieldViolation;
import com.moveinsync.shuttle.metrics.ShuttleMetrics;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ShuttleMetrics metrics;

    public GlobalExceptionHandler(ShuttleMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> api(ApiException ex, HttpServletRequest req) {
        metrics.rejected(ex.getCode());
        log.warn("event=request_rejected code={} status={} path={}", ex.getCode(), ex.getStatus().value(), req.getRequestURI());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage())).toList();
        metrics.rejected("VALIDATION_FAILED");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", req, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> malformed(Exception ex, HttpServletRequest req) {
        metrics.rejected("MALFORMED_REQUEST");
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body or parameter is malformed", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException ex, HttpServletRequest req) {
        metrics.rejected("FORBIDDEN");
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> unauthenticated(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required", req, null);
    }

    /** Lock wait timeout / deadlock victim: safe to retry, nothing was changed. */
    @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<ErrorResponse> busy(Exception ex, HttpServletRequest req) {
        metrics.rejected("TEMPORARILY_BUSY");
        log.warn("event=lock_contention path={} error={}", req.getRequestURI(), ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "1").body(
                body(HttpStatus.SERVICE_UNAVAILABLE, "TEMPORARILY_BUSY", "The trip is busy, please retry shortly", req, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        metrics.rejected("DATA_CONFLICT");
        log.warn("event=data_conflict path={}", req.getRequestURI());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof org.springframework.web.ErrorResponse er) { // 404 / 405 / 415 etc. raised by Spring MVC itself
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return build(status, "REQUEST_ERROR", status.getReasonPhrase(), req, null);
        }
        String errorId = UUID.randomUUID().toString().substring(0, 8);
        log.error("event=unexpected_error errorId={} path={}", errorId, req.getRequestURI(), ex); // stack trace only in logs
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected error. Quote reference " + errorId + " to support.", req, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String msg, HttpServletRequest req,
                                                List<FieldViolation> violations) {
        return ResponseEntity.status(status).body(body(status, code, msg, req, violations));
    }

    private ErrorResponse body(HttpStatus status, String code, String msg, HttpServletRequest req,
                               List<FieldViolation> violations) {
        return new ErrorResponse(Instant.now(), status.value(), code, msg, req.getRequestURI(), MDC.get("requestId"), violations);
    }
}
