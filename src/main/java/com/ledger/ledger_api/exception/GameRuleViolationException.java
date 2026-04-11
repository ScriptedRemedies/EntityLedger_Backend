package com.ledger.ledger_api.exception;

// Used for all the variant logic failures: "Not enough funds", "Killer is on cooldown", "Perk was already used in Iron Man", etc.

public class GameRuleViolationException extends RuntimeException {
    public GameRuleViolationException(String message) {
        super(message);
    }
}
