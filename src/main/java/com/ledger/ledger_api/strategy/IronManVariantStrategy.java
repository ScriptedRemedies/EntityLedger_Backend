package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.SeasonRoster;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component("IRON_MAN")
public class IronManVariantStrategy implements VariantStrategy {

    // --- STEP 1: INITIALIZE ---
    @Override
    public void initializeSeasonState(Season season, Map<String, Object> requestConfig) {
        Map<String, Object> state = season.getVariantState();

        state.put("mulliganCount", 1);
        state.put("runDead", false);

        // Trackers to prevent using the same perks/addons twice
        state.put("usedPerks", new ArrayList<String>());
        state.put("usedAddOns", new ArrayList<String>());

        // Tracker for the full roster rotation
        state.put("playedKillers", new ArrayList<String>());

        // Start the clock
        state.put("lastTrialEndTime", LocalDateTime.now().toString());

        season.setVariantState(state);
    }

    // --- STEP 2: VALIDATE ---
    @Override
    @SuppressWarnings("unchecked")
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        Map<String, Object> state = season.getVariantState();

        // 1. Is the run already dead?
        if ((boolean) state.getOrDefault("runDead", false)) {
            throw new GameRuleViolationException("This Iron Man run has ended in failure.");
        }

        // 2. Time Validation (The 75-Minute Absolute Max Safety Net)
        String lastTrialEndTimeStr = (String) state.get("lastTrialEndTime");
        if (lastTrialEndTimeStr != null) {
            LocalDateTime lastTrialEndTime = LocalDateTime.parse(lastTrialEndTimeStr);
            long minutesSinceLastTrial = Duration.between(lastTrialEndTime, LocalDateTime.now()).toMinutes();

            // 60 mins on results screen + 15 mins on start screen = 75 min hard limit
            if (minutesSinceLastTrial > 75) {
                state.put("runDead", true);
                season.setVariantState(state);
                throw new GameRuleViolationException("You exceeded the maximum allowed break time of 75 total minutes. Run forfeited.");
            }
        }

        // 3. Roster Rotation Validation
        List<String> playedKillers = (List<String>) state.get("playedKillers");
        String killerIdStr = killerRoster.getKiller().getId().toString();

        if (playedKillers != null && playedKillers.contains(killerIdStr)) {
            throw new GameRuleViolationException("You must cycle through the entire roster before repeating " + killerRoster.getKiller().getName() + ".");
        }
    }

    // --- STEP 3: APPLY RESULTS ---
    @Override
    @SuppressWarnings("unchecked")
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();
        int mulliganCount = (int) state.getOrDefault("mulliganCount", 0);
        boolean runDead = (boolean) state.getOrDefault("runDead", false);
        String currentKillerId = killerRoster.getKiller().getId().toString();

        List<String> usedPerks = (List<String>) state.get("usedPerks");
        List<String> usedAddOns = (List<String>) state.get("usedAddOns");
        List<String> playedKillers = (List<String>) state.get("playedKillers");

        // 1. UNIQUE PERK & ADD-ON VALIDATION
        boolean duplicateFound = false;

        if (request.perkIds() != null) {
            for (Long perkId : request.perkIds()) {
                if (usedPerks.contains(perkId.toString())) duplicateFound = true;
                else usedPerks.add(perkId.toString());
            }
        }

        if (request.addOnIds() != null) {
            for (Long addOnId : request.addOnIds()) {
                if (usedAddOns.contains(addOnId.toString())) duplicateFound = true;
                else usedAddOns.add(addOnId.toString());
            }
        }

        // If they cheated/accidentally used a locked perk, instant death
        if (duplicateFound) {
            runDead = true;
        } else {
            // 2. ESCAPE PENALTY & MULLIGAN
            // Note: Make sure TrialSurvivor.SurvivorOutcome matches your actual enum!
            boolean gateEscape = request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.ESCAPED);

            if (gateEscape) {
                if (mulliganCount > 0) {
                    mulliganCount--; // Save the run, burn the token
                } else {
                    runDead = true;  // No token left, run over
                }
            }

            // 3. EARN MULLIGAN
            if (mulliganCount == 0 && request.kills() == 4 && request.gensLeft() == 5) {
                mulliganCount = 1;
            }

            // 4. ROSTER ROTATION MANAGEMENT
            playedKillers.add(currentKillerId);

            // Count how many killers actually exist in the available pool
            long availableKillersCount = season.getRosters().stream()
                    .filter(r -> r.getStatus() == SeasonRoster.RosterStatus.AVAILABLE)
                    .count();

            // If they have played everyone, clear the list so the rotation starts over
            if (playedKillers.size() >= availableKillersCount) {
                playedKillers.clear();
            }
        }

        // Update State
        state.put("mulliganCount", mulliganCount);
        state.put("runDead", runDead);
        state.put("usedPerks", usedPerks);
        state.put("usedAddOns", usedAddOns);
        state.put("playedKillers", playedKillers);

        // Reset the timer for their next break
        state.put("lastTrialEndTime", LocalDateTime.now().toString());

        season.setVariantState(state);
    }

    // --- STEP 4: END GAME ---
    @Override
    public boolean isSeasonOver(Season season) {
        Map<String, Object> state = season.getVariantState();

        // Failure
        if ((boolean) state.getOrDefault("runDead", false)) {
            return true;
        }

        // Success
        if (season.getCurrentGrade() != null && season.getCurrentGrade().name().equals("IRIDESCENT_1")) {
            return true;
        }

        return false;
    }
}
