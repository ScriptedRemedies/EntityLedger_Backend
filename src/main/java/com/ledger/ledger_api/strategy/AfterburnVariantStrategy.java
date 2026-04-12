package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("AFTERBURN")
public class AfterburnVariantStrategy implements VariantStrategy {

    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD || killerRoster.getStatus() == SeasonRoster.RosterStatus.SOLD) {
            throw new GameRuleViolationException("This killer is dead or sold and cannot be played.");
        }

        Map<String, Object> state = season.getVariantState();
        Integer balance = (Integer) state.getOrDefault("balance", 0);

        if (balance < 0) {
            throw new GameRuleViolationException("Your balance is negative. You must sell a killer before playing.");
        }
    }

    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        if (request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE)) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        }

        Map<String, Object> state = season.getVariantState();
        Integer balance = (Integer) state.getOrDefault("balance", 0);

        // Placeholder for economy math
        int trialIncome = calculateIncome(request, trial.getPipProgression());
        state.put("balance", balance + trialIncome);
        season.setVariantState(state);
    }

    private int calculateIncome(TrialSubmitRequest request, int pips) {
        return pips > 0 ? 5 : -2;
    }
}
