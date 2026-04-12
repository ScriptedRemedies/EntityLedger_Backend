package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("STANDARD")
public class StandardVariantStrategy implements VariantStrategy {

    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }

        Map<String, Object> state = season.getVariantState();
        Boolean consecutiveMatches = (Boolean) state.getOrDefault("consecutiveMatches", false);
        Long lastPlayedKillerId = state.get("lastPlayedKillerId") != null ? ((Number) state.get("lastPlayedKillerId")).longValue() : null;

        // If consecutive matches are on, they must keep playing the same killer until it dies
        if (consecutiveMatches && lastPlayedKillerId != null && !killerRoster.getKiller().getId().equals(lastPlayedKillerId)) {
            throw new GameRuleViolationException("Consecutive matches rule is active. You must continue playing the current killer.");
        }
    }

    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        boolean hatchEscapeOccurred = request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE);

        if (hatchEscapeOccurred) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);

            // Clear the active killer so they can pick a new one next trial
            Map<String, Object> state = season.getVariantState();
            state.remove("lastPlayedKillerId");
            season.setVariantState(state);
        } else {
            // Update the last played killer for the consecutive matches rule
            Map<String, Object> state = season.getVariantState();
            state.put("lastPlayedKillerId", killerRoster.getKiller().getId());
            season.setVariantState(state);
        }
    }
}
