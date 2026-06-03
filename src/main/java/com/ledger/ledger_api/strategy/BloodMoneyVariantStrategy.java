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

@Component("BLOOD_MONEY")
public class BloodMoneyVariantStrategy implements VariantStrategy {

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

        // Economy
        state.put("balance", 20);

        // Cooldown State
        state.put("cooldownKillerId", null);
        state.put("cooldownTrialsLeft", 0);

        // Streak State
        state.put("currentStreakKillerId", null);
        state.put("consecutiveWins", 0);

        season.setVariantState(state);
    }

    // --- STEP 2: VALIDATE ---
    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        Map<String, Object> state = season.getVariantState();

        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.SOLD) {
            throw new GameRuleViolationException("This killer was sold and cannot be played.");
        }

        String cooldownKillerId = (String) state.get("cooldownKillerId");
        if (cooldownKillerId != null && cooldownKillerId.equals(killerRoster.getKiller().getId().toString())) {
            throw new GameRuleViolationException("This killer is currently on cooldown.");
        }

        int balance = (int) state.getOrDefault("balance", 0);
        if (balance < 0) {
            throw new GameRuleViolationException("You have a negative balance. You must sell a killer to afford this trial.");
        }
    }

    // --- STEP 3: APPLY RESULTS ---
    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();
        String currentKillerId = killerRoster.getKiller().getId().toString();

        // 1. DETERMINE WIN / LOSS & PERMADEATH
        boolean gateEscape = request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.ESCAPED);
        long kills = request.survivorOutcomes().stream()
                .filter(o -> o == TrialSurvivor.SurvivorOutcome.KILLED || o == TrialSurvivor.SurvivorOutcome.SACRIFICED)
                .count();

        boolean isWin = (kills >= 3) && !gateEscape;

        if (gateEscape) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        }

        // 2. PROCESS COOLDOWN DECREMENT
        int cooldownTrialsLeft = (int) state.getOrDefault("cooldownTrialsLeft", 0);
        if (cooldownTrialsLeft > 0) {
            cooldownTrialsLeft--;
            if (cooldownTrialsLeft == 0) {
                state.put("cooldownKillerId", null);
            }
            state.put("cooldownTrialsLeft", cooldownTrialsLeft);
        }

        // 3. PROCESS WIN STREAK & NEW COOLDOWNS
        String streakKillerId = (String) state.get("currentStreakKillerId");
        int consecutiveWins = (int) state.getOrDefault("consecutiveWins", 0);

        if (isWin && !gateEscape) {
            if (currentKillerId.equals(streakKillerId)) {
                consecutiveWins++;
            } else {
                streakKillerId = currentKillerId;
                consecutiveWins = 1;
            }

            if (consecutiveWins >= 2) {
                // Check if they have more than 1 living/unsold killer left
                long aliveKillers = season.getRosters().stream()
                        .filter(r -> r.getStatus() != SeasonRoster.RosterStatus.DEAD && r.getStatus() != SeasonRoster.RosterStatus.SOLD)
                        .count();

                if (aliveKillers > 1) {
                    state.put("cooldownKillerId", currentKillerId);
                    state.put("cooldownTrialsLeft", 2);
                }
                consecutiveWins = 0;
                streakKillerId = null;
            }
        } else {
            // Loss resets the streak
            consecutiveWins = 0;
            streakKillerId = null;
        }

        long remainingAlive = season.getRosters().stream()
                .filter(r -> r.getStatus() != SeasonRoster.RosterStatus.DEAD && r.getStatus() != SeasonRoster.RosterStatus.SOLD)
                .count();

        // If the trial resulted in only 1 killer remaining, wipe the active cooldown
        if (remainingAlive <= 1) {
            state.put("cooldownKillerId", null);
            state.put("cooldownTrialsLeft", 0);
        }

        state.put("currentStreakKillerId", streakKillerId);
        state.put("consecutiveWins", consecutiveWins);

        // 4. THE ECONOMY: EXPENSES
        int loadoutCost = killerRoster.getKiller().getCost();
        loadoutCost += trial.getPerks().stream().mapToInt(Perk::getCost).sum();
        loadoutCost += trial.getAddOns().stream().mapToInt(AddOn::getCost).sum();

        // 5. THE ECONOMY: BONUSES & PENALTIES
        int earnings = 0;
        int penalties = 0;

        // --- Kills for Money ---
        if (request.kills() != null) {
            if (request.kills() == 3) {
                earnings += 10;
            } else if (request.kills() == 4) {
                earnings += 20;
            }
        }

        // Bonuses
        if (request.kills() != null && request.gensLeft() != null) {
            if (request.kills() == 4 && request.gensLeft() == 5) {
                earnings += 4;
            } else if (request.kills() == 4 && request.gensLeft() == 4) {
                earnings += 2;
            }
        }

        // Penalties
        if (request.genBeforeHook() != null && request.genBeforeHook()) penalties += 2;
        if (request.lastGenCompleted() != null && request.lastGenCompleted()) penalties += 2;
        if (request.gateOpened() != null && request.gateOpened()) penalties += 5;
        if (request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE)) penalties += 4;

        int netIncome = earnings - penalties - loadoutCost;
        trial.setNetIncome(netIncome);

        // Apply to Balance
        int balance = (int) state.getOrDefault("balance", 20);
        balance = balance + netIncome;
        state.put("balance", balance);

        season.setVariantState(state);

        // 6. MAP SURVIVORS
        List<TrialSurvivor> trialSurvivors = request.survivorOutcomes().stream().map(outcome -> {
            TrialSurvivor survivor = new TrialSurvivor();
            survivor.setTrial(trial);
            survivor.setOutcome(outcome);
            return survivor;
        }).collect(Collectors.toList());

        trial.setSurvivors(trialSurvivors);

        // 7. PIP & GRADE MATH
        String currentGrade = season.getCurrentGrade() != null ? season.getCurrentGrade().name() : "ASH_IV";
        int currentPips = season.getCurrentPips() != null ? season.getCurrentPips() : 0;
        int pipChange = request.pipProgression() != null ? request.pipProgression() : 0;

        int newPips = currentPips + pipChange;
        int gradeIndex = GRADE_PROGRESSION.indexOf(currentGrade);

        while (gradeIndex < GRADE_PROGRESSION.size() - 1 && newPips >= getMaxPipsForGrade(GRADE_PROGRESSION.get(gradeIndex))) {
            newPips -= getMaxPipsForGrade(GRADE_PROGRESSION.get(gradeIndex));
            gradeIndex++;
        }

        if (newPips < 0) {
            newPips = 0;
        }

        String newGradeName = GRADE_PROGRESSION.get(gradeIndex);
        season.setCurrentGrade(GradeRule.Grade.valueOf(newGradeName));
        season.setCurrentPips(newPips);
        trial.setResultingGrade(GradeRule.Grade.valueOf(newGradeName));
        trial.setResultingPips(newPips);
    }

    // --- STEP 4: END GAME ---
    @Override
    public Season.SeasonStatus isSeasonOver(Season season) {
        Map<String, Object> state = season.getVariantState();
        int balance = (int) state.getOrDefault("balance", 0);

        // Success Condition
        if (season.getCurrentGrade() != null && season.getCurrentGrade().name().equals("IRIDESCENT_I")) {
            return Season.SeasonStatus.COMPLETED;
        }

        // Failure Condition 1: Entire Roster is Dead/Sold
        long aliveKillers = season.getRosters().stream()
                .filter(r -> r.getStatus() != SeasonRoster.RosterStatus.DEAD && r.getStatus() != SeasonRoster.RosterStatus.SOLD)
                .count();

        if (aliveKillers == 0) {
            return Season.SeasonStatus.FAILED_ROSTER;
        }

        // Failure Condition 2: Bankrupt and Cannot Sell
        if (balance < 0 && aliveKillers == 0) {
            return Season.SeasonStatus.FAILED_ROSTER;
        }

        return Season.SeasonStatus.ACTIVE;
    }
}
