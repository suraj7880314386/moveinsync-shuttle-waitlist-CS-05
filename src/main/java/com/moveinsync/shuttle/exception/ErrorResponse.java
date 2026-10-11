package com.moveinsync.shuttle.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(Instant timestamp, int status, String code, String message, String path,
                            String requestId, List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {}
}
