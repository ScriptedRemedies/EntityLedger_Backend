package com.ledger.ledger_api.exception;

// Used when a user tries to fetch a Trial, Season, or Killer that doesn't exist in the database

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
