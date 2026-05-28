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

@Component("CHAOS_SHUFFLE")
public class ChaosShuffleVariantStrategy implements VariantStrategy {

    // --- INITIALIZE ---
    @Override
    public void initializeSeasonState(Season season, Map<String, Object> requestConfig) {
        Map<String, Object> state = season.getVariantState();

        // Start with 3 re roll tokens
        state.put("rerollTokens", 3);

        // Setup trackers for the cooldown logic
        state.put("lastPlayedKillerId", null);
        state.put("consecutiveCount", 0);
        state.put("cooldowns", new HashMap<String, Integer>());

        season.setVariantState(state);
    }

    // --- VALIDATE ---
    @Override
    @SuppressWarnings("unchecked")
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        // Check if dead
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }

        // Check if on cooldown
        long availableKillersCount = season.getRosters().stream()
                .filter(r -> r.getStatus() == SeasonRoster.RosterStatus.AVAILABLE)
                .count();

        if (availableKillersCount > 1) {
            Map<String, Integer> cooldowns = (Map<String, Integer>) season.getVariantState().get("cooldowns");
            String killerIdStr = killerRoster.getKiller().getId().toString();

            if (cooldowns != null && cooldowns.getOrDefault(killerIdStr, 0) > 0) {
                throw new GameRuleViolationException("This killer is on cooldown for " + cooldowns.get(killerIdStr) + " more trial(s).");
            }
        }
    }

    // --- APPLY RESULTS ---
    @Override
    @SuppressWarnings("unchecked")
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();
        String currentKillerId = killerRoster.getKiller().getId().toString();

        // PERMADEATH (Check for Gate Escape)
        if (request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.ESCAPED)) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        }

        // TOKENS (Earn a token for 4K with 4 or 5 gens left)
        Integer rerollTokens = (Integer) state.get("rerollTokens");

        // 2 Tokens for 5 gens, 1 token for 4 gens
        if (request.kills() == 4 && request.gensLeft() == 5) {
            state.put("rerollTokens", rerollTokens + 2);
        } else if (request.kills() == 4 && request.gensLeft() == 4) {
            state.put("rerollTokens", rerollTokens + 1);
        }

        // COOLDOWN MANAGEMENT
        Map<String, Integer> cooldowns = (Map<String, Integer>) state.get("cooldowns");

        // Reduce all active cooldowns by 1 (since a trial just happened)
        cooldowns.entrySet().removeIf(entry -> {
            int newCooldown = entry.getValue() - 1;
            entry.setValue(newCooldown);
            return newCooldown <= 0; // Remove them from the map if cooldown hits 0
        });

        // Handle the Consecutive Plays Rule
        String lastPlayedId = (String) state.get("lastPlayedKillerId");
        int consecutiveCount = (int) state.get("consecutiveCount");

        if (currentKillerId.equals(lastPlayedId)) {
            consecutiveCount++;
            if (consecutiveCount >= 2) {
                // They played them twice in a row! Apply the 2-trial penalty.
                cooldowns.put(currentKillerId, 2);
                // Reset the trackers so they start fresh next time
                consecutiveCount = 0;
                lastPlayedId = null;
            }
        } else {
            // They played a different killer, reset the tracker to 1 for this new killer
            lastPlayedId = currentKillerId;
            consecutiveCount = 1;
        }

        // SAVE STATE BACK TO SEASON
        state.put("cooldowns", cooldowns);
        state.put("consecutiveCount", consecutiveCount);
        state.put("lastPlayedKillerId", lastPlayedId);
        season.setVariantState(state);
    }

    // --- END GAME CHECK ---
    // TODO: Properly write the end of season for this variant
    @Override
    public Season.SeasonStatus isSeasonOver(Season season) {
        // Success Condition: Reached Iridescent 1
        if (season.getCurrentGrade() != null && season.getCurrentGrade().name().equals("IRIDESCENT_I")) {
            return Season.SeasonStatus.COMPLETED;
        }

        // Failure Condition: All killers are dead
        boolean allDead = season.getRosters().stream()
                .allMatch(roster -> roster.getStatus() == SeasonRoster.RosterStatus.DEAD);

        if (allDead) {
            return Season.SeasonStatus.FAILED_ROSTER;
        }

        // If neither condition is met, the season continues
        return Season.SeasonStatus.ACTIVE;
    }
}
