package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component("CHAOS_SHUFFLE")
public class ChaosShuffleVariantStrategy implements VariantStrategy {

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
        return 5;
    }

    // --- STEP 1: INITIALIZE ---
    @Override
    public void initializeSeasonState(Season season, Map<String, Object> requestConfig) {
        Map<String, Object> state = new HashMap<>();

        state.put("reRollTokens", 3);
        state.put("currentStreakKillerId", null);
        state.put("currentStreak", 0);
        state.put("cooldownKillerId", null);
        state.put("cooldownTrialsLeft", 0);

        season.setVariantState(state);
    }

    // --- STEP 2: VALIDATE ---
    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }

        Map<String, Object> state = season.getVariantState();
        String cooldownKillerId = (String) state.get("cooldownKillerId");

        if (cooldownKillerId != null && cooldownKillerId.equals(killerRoster.getKiller().getId().toString())) {
            throw new GameRuleViolationException("This killer is currently on a cooldown and cannot be used.");
        }
    }

    // --- STEP 3: APPLY RESULTS ---
    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();
        String killerIdStr = killerRoster.getKiller().getId().toString();

        // 1. ATTACH SURVIVORS & CALCULATE MATCH OUTCOME
        int kills = 0;
        boolean gateEscape = false;

        List<TrialSurvivor> trialSurvivors = request.survivorOutcomes().stream().map(outcome -> {
            TrialSurvivor survivor = new TrialSurvivor();
            survivor.setTrial(trial);
            survivor.setOutcome(outcome);
            return survivor;
        }).collect(Collectors.toList());

        trial.setSurvivors(trialSurvivors);

        for (TrialSurvivor.SurvivorOutcome outcome : request.survivorOutcomes()) {
            if (outcome == TrialSurvivor.SurvivorOutcome.ESCAPED) gateEscape = true;
            if (outcome == TrialSurvivor.SurvivorOutcome.KILLED || outcome == TrialSurvivor.SurvivorOutcome.SACRIFICED) kills++;
        }

        boolean trialWon = (kills >= 3 && !gateEscape);

        // 2. TOKEN ECONOMY
        int currentTokens = (int) state.getOrDefault("reRollTokens", 0);

        // Deduct if used
        if (Boolean.TRUE.equals(request.usedReRollToken())) {
            currentTokens = Math.max(0, currentTokens - 1);
        }

        // Bonus for 4K with Gens remaining
        if (kills == 4 && request.gensLeft() != null) {
            if (request.gensLeft() == 4) currentTokens += 1;
            if (request.gensLeft() == 5) currentTokens += 2;
        }
        state.put("reRollTokens", currentTokens);

        // 3. COOLDOWN TICK
        int cooldownLeft = (int) state.getOrDefault("cooldownTrialsLeft", 0);
        if (cooldownLeft > 0) {
            cooldownLeft--;
            state.put("cooldownTrialsLeft", cooldownLeft);
            if (cooldownLeft == 0) {
                state.put("cooldownKillerId", null);
            }
        }

        // 4. PERMADEATH vs STREAK
        if (gateEscape) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
            state.put("currentStreakKillerId", null);
            state.put("currentStreak", 0);
        } else if (trialWon) {
            String streakId = (String) state.get("currentStreakKillerId");
            int streak = (int) state.getOrDefault("currentStreak", 0);

            if (killerIdStr.equals(streakId)) {
                streak++;
            } else {
                streakId = killerIdStr;
                streak = 1;
            }

            // Check for cooldown trigger
            if (streak >= 2) {
                state.put("cooldownKillerId", killerIdStr);
                state.put("cooldownTrialsLeft", 2);
                state.put("currentStreakKillerId", null);
                state.put("currentStreak", 0);
            } else {
                state.put("currentStreakKillerId", streakId);
                state.put("currentStreak", streak);
            }
        } else {
            // Did not win, did not die (e.g. 2 kills, hatch escape). Breaks streak.
            state.put("currentStreakKillerId", null);
            state.put("currentStreak", 0);
        }

        season.setVariantState(state);

        // Safe cooldown logic, removes cooldown if only one killer is alive
        long aliveCount = season.getRosters().stream()
                .filter(r -> r.getStatus() != SeasonRoster.RosterStatus.DEAD)
                .count();

        // If only 1 killer remains, clear the cooldown completely so the player isn't stuck
        if (aliveCount <= 1) {
            state.put("cooldownKillerId", null);
            state.put("cooldownTrialsLeft", 0);
            season.setVariantState(state); // Save the cleared state
        }

        // 5. PIP MATH
        int currentPips = season.getCurrentPips() != null ? season.getCurrentPips() : 0;
        int pipChange = request.pipProgression() != null ? request.pipProgression() : 0;
        String currentGrade = season.getCurrentGrade() != null ? season.getCurrentGrade().name() : "ASH_IV";

        int newPips = currentPips + pipChange;
        int gradeIndex = GRADE_PROGRESSION.indexOf(currentGrade);

        while (gradeIndex < GRADE_PROGRESSION.size() - 1 && newPips >= getMaxPipsForGrade(GRADE_PROGRESSION.get(gradeIndex))) {
            newPips -= getMaxPipsForGrade(GRADE_PROGRESSION.get(gradeIndex));
            gradeIndex++;
        }

        if (newPips < 0) newPips = 0;

        String newGradeName = GRADE_PROGRESSION.get(gradeIndex);

        season.setCurrentGrade(GradeRule.Grade.valueOf(newGradeName));
        season.setCurrentPips(newPips);
        trial.setResultingGrade(GradeRule.Grade.valueOf(newGradeName));
        trial.setResultingPips(newPips);
    }

    // --- STEP 4: END GAME ---
    @Override
    public Season.SeasonStatus isSeasonOver(Season season) {
        if (season.getCurrentGrade() != null && season.getCurrentGrade().name().equals("IRIDESCENT_I")) {
            return Season.SeasonStatus.COMPLETED;
        }

        boolean allDead = season.getRosters().stream()
                .allMatch(roster -> roster.getStatus() == SeasonRoster.RosterStatus.DEAD);

        if (allDead) {
            return Season.SeasonStatus.FAILED_ROSTER;
        }

        return Season.SeasonStatus.ACTIVE;
    }
}
