package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import java.util.HashMap;
import java.util.Map;

@Component("STANDARD")
public class StandardVariantStrategy implements VariantStrategy {

    private static final List<String> GRADE_PROGRESSION = Arrays.asList(
            "ASH_IV", "ASH_III", "ASH_II", "ASH_I",
            "BRONZE_IV", "BRONZE_III", "BRONZE_II", "BRONZE_I",
            "SILVER_IV", "SILVER_III", "SILVER_II", "SILVER_I",
            "GOLD_IV", "GOLD_III", "GOLD_II", "GOLD_I",
            "IRIDESCENT_IV", "IRIDESCENT_III", "IRIDESCENT_II", "IRIDESCENT_I"
    );

    private int getMaxPipsForGrade(String grade) {
        if (grade.startsWith("ASH")) return 3;
        if (grade.startsWith("BRONZE")) return 4;
        return 5; // Silver, Gold, and Iridescent require 5 pips
    }

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

            // Find the next killer in the roster who is NOT dead
            SeasonRoster nextAvailable = season.getRosters().stream()
                    .filter(r -> r.getStatus() != SeasonRoster.RosterStatus.DEAD
                            && !r.getId().equals(killerRoster.getId()))
                    .findFirst()
                    .orElse(null);

            if (nextAvailable != null) {
                state.put("lastPlayedKillerId", nextAvailable.getKiller().getId().toString());
            } else {
                state.put("lastPlayedKillerId", null); // Everyone is dead!
            }
        } else {
            state.put("lastPlayedKillerId", killerRoster.getKiller().getId().toString());
        }

        List<TrialSurvivor> trialSurvivors = request.survivorOutcomes().stream().map(outcome -> {
            TrialSurvivor survivor = new TrialSurvivor();
            survivor.setTrial(trial);
            survivor.setOutcome(outcome);
            return survivor;
        }).collect(Collectors.toList());

        trial.setSurvivors(trialSurvivors);

        season.setVariantState(state);

        // 1. Get current state (fallback to ASH_IV and 0 if null)
        String currentGrade = season.getCurrentGrade() != null ? season.getCurrentGrade().name() : "ASH_IV";
        int currentPips = season.getCurrentPips() != null ? season.getCurrentPips() : 0;
        int pipChange = request.pipProgression() != null ? request.pipProgression() : 0;

        int newPips = currentPips + pipChange;
        int gradeIndex = GRADE_PROGRESSION.indexOf(currentGrade);

// 2. Handle Promotions (Pip Gain)
        while (gradeIndex < GRADE_PROGRESSION.size() - 1 && newPips >= getMaxPipsForGrade(GRADE_PROGRESSION.get(gradeIndex))) {
            newPips -= getMaxPipsForGrade(GRADE_PROGRESSION.get(gradeIndex));
            gradeIndex++;
        }

// 3. Handle Demotions (Pip Loss)
        if (newPips < 0) {
            // In Dead by Daylight, you cannot drop below 0 pips in your current grade.
            newPips = 0;
        }

// 4. Save the new values to the Season
        String newGradeName = GRADE_PROGRESSION.get(gradeIndex);
// Assuming your enum is named Grade, replace with your actual Enum class name
        season.setCurrentGrade(GradeRule.Grade.valueOf(newGradeName));
        season.setCurrentPips(newPips);
        trial.setResultingGrade(GradeRule.Grade.valueOf(newGradeName));
        trial.setResultingPips(newPips);
    }

    // --- STEP 4: END GAME ---
    @Override
    public boolean isSeasonOver(Season season) {
        // Success Condition: Reached Iridescent 1
        if (season.getCurrentGrade() != null && season.getCurrentGrade().name().equals("IRIDESCENT_I")) {
            return true;
        }

        // Failure Condition: All killers are dead
        return season.getRosters().stream()
                .allMatch(roster -> roster.getStatus() == SeasonRoster.RosterStatus.DEAD);
    }
}
