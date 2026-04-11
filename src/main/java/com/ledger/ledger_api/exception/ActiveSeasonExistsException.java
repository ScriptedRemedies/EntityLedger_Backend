package com.ledger.ledger_api.exception;

// Because a user can only have one active season at a time, this explicitly handles the edge case where they try to start a new run without finishing or failing their current one.

public class ActiveSeasonExistsException extends RuntimeException {
    public ActiveSeasonExistsException(String message) {
        super(message);
    }
}
