package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("BLOOD_MONEY")
public class BloodMoneyVariantStrategy implements VariantStrategy {

    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.SOLD) {
            throw new GameRuleViolationException("This killer was sold and cannot be played.");
        }

        Map<String, Object> state = season.getVariantState();
        Integer balance = (Integer) state.getOrDefault("balance", 0);

        if (balance < 0) {
            throw new GameRuleViolationException("Your balance is negative. You must sell a killer before playing a trial.");
        }
    }

    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();
        Integer balance = (Integer) state.getOrDefault("balance", 0);

        boolean hatchEscapeOccurred = false;

        // Loop through survivors to check for permadeath conditions
        for (TrialSurvivor.SurvivorOutcome outcome : request.survivorOutcomes()) {
            if (outcome == TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE) {
                hatchEscapeOccurred = true;
            }
        }

        // Apply Permadeath
        if (hatchEscapeOccurred) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        }

        // TODO: You will plug in your specific math here for calculating income
        // based on kills, pips, and perk costs. For now, we update a placeholder value.
        int trialIncome = calculateBloodMoneyIncome(request, trial.getPipProgression());

        state.put("balance", balance + trialIncome);
        season.setVariantState(state);
    }

    private int calculateBloodMoneyIncome(TrialSubmitRequest request, int pips) {
        // Placeholder for your specific economic rules
        int income = 0;
        if (pips > 0) income += 5; // Example logic
        return income;
    }
}
