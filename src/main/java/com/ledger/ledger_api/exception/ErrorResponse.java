package com.ledger.ledger_api.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        int statusCode,
        String message,
        LocalDateTime timestamp
) {}
