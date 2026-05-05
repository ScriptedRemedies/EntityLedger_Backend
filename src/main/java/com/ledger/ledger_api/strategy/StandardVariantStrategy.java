package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component("STANDARD")
public class StandardVariantStrategy implements VariantStrategy {

    // --- STEP 1: INITIALIZE ---
    @Override
    public void initializeSeasonState(Season season, Map<String, Object> requestConfig) {
        Map<String, Object> state = new HashMap<>();

        state.put("lastPlayedKillerId", null);

        season.setVariantState(state);
    }

    // --- STEP 2: VALIDATE ---
    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }

        Map<String, Object> state = season.getVariantState();
    }

    // --- STEP 3: APPLY RESULTS & ENFORCE CUSTOM RULES ---
    @Override
    @SuppressWarnings("unchecked")
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();

        // Permadeath (Gate Escape)
        boolean gateEscape = request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.ESCAPED);

        if (gateEscape) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
            // Clear the active killer so they can pick a new one next trial
            state.put("lastPlayedKillerId", null);
        } else {
            // Update the last played killer for the consecutive matches rule
            state.put("lastPlayedKillerId", killerRoster.getKiller().getId().toString());
        }

        season.setVariantState(state);
    }

    // --- STEP 4: END GAME ---
    @Override
    public boolean isSeasonOver(Season season) {
        // Success Condition: Reached Iridescent 1
        if (season.getCurrentGrade() != null && season.getCurrentGrade().name().equals("IRIDESCENT_1")) {
            return true;
        }

        // Failure Condition: All killers are dead
        return season.getRosters().stream()
                .allMatch(roster -> roster.getStatus() == SeasonRoster.RosterStatus.DEAD);
    }
}
