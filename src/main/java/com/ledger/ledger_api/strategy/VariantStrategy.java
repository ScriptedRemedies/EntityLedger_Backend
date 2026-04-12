package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;

public interface VariantStrategy {

    // Checks conditions BEFORE a trial starts (e.g., Is killer dead? Are funds negative?)
    void validateTrialStart(Season season, SeasonRoster killerRoster);

    // Applies consequences AFTER a trial ends (e.g., Deduct funds, kill character, use tokens)
    void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request);
}
