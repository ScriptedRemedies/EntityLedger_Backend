package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("CHAOS_SHUFFLE")
public class ChaosShuffleVariantStrategy implements VariantStrategy {

    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }
    }

    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        if (request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE)) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        }

        Map<String, Object> state = season.getVariantState();
        Integer rerollTokens = (Integer) state.getOrDefault("rerollTokens", 0);

        // Earn a token if criteria is met (e.g., 4K or double pip)
        if (trial.getPipProgression() >= 2) {
            state.put("rerollTokens", rerollTokens + 1);
        }

        season.setVariantState(state);
    }
}
