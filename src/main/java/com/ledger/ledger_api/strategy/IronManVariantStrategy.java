package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("IRON_MAN")
public class IronManVariantStrategy implements VariantStrategy {

    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead.");
        }
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.COOLDOWN) {
            throw new GameRuleViolationException("This killer is on cooldown and must rest for one match.");
        }
    }

    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        long escapes = request.survivorOutcomes().stream()
                .filter(o -> o == TrialSurvivor.SurvivorOutcome.ESCAPED || o == TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE)
                .count();

        boolean hatchEscape = request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE);

        Map<String, Object> state = season.getVariantState();
        Integer mulliganTokens = (Integer) state.getOrDefault("mulliganToken", 0);

        // Permadeath Rules
        if (hatchEscape) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        } else if (escapes == 1) { // A 3K with an exit gate escape
            if (mulliganTokens > 0) {
                // Forgive the escape, consume the token
                state.put("mulliganToken", mulliganTokens - 1);
                killerRoster.setStatus(SeasonRoster.RosterStatus.COOLDOWN); // Still goes on cooldown
            } else {
                // No token to save them
                killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
            }
        } else if (escapes > 1) {
            // 2K or worse is immediate death
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        } else {
            // 4K - Survivor goes on cooldown
            killerRoster.setStatus(SeasonRoster.RosterStatus.COOLDOWN);

            // Example logic: Earn a mulligan if none held and it was a perfect game
            if (mulliganTokens == 0 && trial.getPipProgression() >= 2) {
                state.put("mulliganToken", 1);
            }
        }

        season.setVariantState(state);
    }
}
