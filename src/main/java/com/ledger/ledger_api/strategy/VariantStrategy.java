package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;

import java.util.Map;

public interface VariantStrategy {

    // NEW: Called when a season is created. It extracts the necessary config (like starting funds)
    // from the initial request and sets up the Season's internal Map<String, Object> state.
    void initializeSeasonState(Season season, Map<String, Object> requestConfig);

    // Checks conditions BEFORE a trial starts (e.g., Is killer dead? Are funds negative?)
    void validateTrialStart(Season season, SeasonRoster killerRoster);

    // Applies consequences AFTER a trial ends (e.g., Deduct funds, kill character, use tokens)
    void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request);

    // NEW: The SeasonService will call this after applyTrialResults.
    // If it returns true, the backend automatically flips the Season status to COMPLETED.
    boolean isSeasonOver(Season season);
}
