package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component("STANDARD")
public class StandardVariantStrategy implements VariantStrategy {

    // --- STEP 1: INITIALIZE ---
    @Override
    public void initializeSeasonState(Season season, Map<String, Object> requestConfig) {
        Map<String, Object> state = new HashMap<>();

        // Extract toggles (default to false if not provided)
        boolean restrictedLoadout = (boolean) requestConfig.getOrDefault("restrictedLoadout", false);
        boolean sameBuild = (boolean) requestConfig.getOrDefault("sameBuild", false);
        boolean consecutiveMatches = (boolean) requestConfig.getOrDefault("consecutiveMatches", false);

        // Enforce Mutual Exclusivity
        if (restrictedLoadout && sameBuild) {
            throw new GameRuleViolationException("You cannot select both 'Restricted Loadout' and 'Same Build'.");
        }

        state.put("restrictedLoadout", restrictedLoadout);
        state.put("sameBuild", sameBuild);
        state.put("consecutiveMatches", consecutiveMatches);
        state.put("lastPlayedKillerId", null);

        // Store specific rule parameters
        if (restrictedLoadout) {
            // Expects a map like: {"ASH": 2, "BRONZE": 2, "SILVER": 1, "GOLD": 0, "IRIDESCENT": 0}
            state.put("addOnLimits", requestConfig.get("addOnLimits"));
        }

        if (sameBuild) {
            // Expects lists of UUID strings representing the locked build
            state.put("lockedPerks", requestConfig.get("lockedPerks"));
            state.put("lockedAddOns", requestConfig.get("lockedAddOns"));
        }

        season.setVariantState(state);
    }

    // --- STEP 2: VALIDATE ---
    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }

        Map<String, Object> state = season.getVariantState();
        boolean consecutiveMatches = (boolean) state.getOrDefault("consecutiveMatches", false);
        String lastPlayedKillerId = (String) state.get("lastPlayedKillerId");
        String currentKillerId = killerRoster.getKiller().getId().toString();

        // If consecutive matches are on, they must keep playing the same killer until it dies
        if (consecutiveMatches && lastPlayedKillerId != null && !currentKillerId.equals(lastPlayedKillerId)) {
            throw new GameRuleViolationException("Consecutive matches rule is active. You must continue playing the current killer until they die.");
        }
    }

    // --- STEP 3: APPLY RESULTS & ENFORCE CUSTOM RULES ---
    @Override
    @SuppressWarnings("unchecked")
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();
        boolean restrictedLoadout = (boolean) state.getOrDefault("restrictedLoadout", false);
        boolean sameBuild = (boolean) state.getOrDefault("sameBuild", false);

        // Convert the requested IDs to strings for easier comparison with JSON state
        List<String> requestedPerks = request.perkIds() != null ?
                request.perkIds().stream().map(String::valueOf).collect(Collectors.toList()) : List.of();
        List<String> requestedAddOns = request.addOnIds() != null ?
                request.addOnIds().stream().map(String::valueOf).collect(Collectors.toList()) : List.of();

        // A. Same Build Validation
        if (sameBuild) {
            List<String> lockedPerks = (List<String>) state.get("lockedPerks");
            List<String> lockedAddOns = (List<String>) state.get("lockedAddOns");

            if (!requestedPerks.containsAll(lockedPerks) || !lockedPerks.containsAll(requestedPerks)) {
                throw new GameRuleViolationException("Same Build rule is active. You cannot change your perks.");
            }
            if (!requestedAddOns.containsAll(lockedAddOns) || !lockedAddOns.containsAll(requestedAddOns)) {
                throw new GameRuleViolationException("Same Build rule is active. You cannot change your add-ons.");
            }
        }

        // B. Restricted Loadout Validation
        if (restrictedLoadout) {
            Map<String, Integer> addOnLimits = (Map<String, Integer>) state.get("addOnLimits");

            // Extract the base tier (e.g., "ASH" from "ASH_IV")
            String currentGrade = season.getCurrentGrade().name();
            String gradeTier = currentGrade.split("_")[0];

            int limit = addOnLimits.getOrDefault(gradeTier, 2);

            if (request.addOnIds() != null && request.addOnIds().size() > limit) {
                throw new GameRuleViolationException("Loadout restrictions active: You can only use " + limit + " add-on(s) at " + gradeTier + " grade.");
            }
        }

        // C. Permadeath (Gate Escape)
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
