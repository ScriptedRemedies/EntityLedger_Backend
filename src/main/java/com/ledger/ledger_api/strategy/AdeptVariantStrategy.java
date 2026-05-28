package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import com.ledger.ledger_api.repository.PerkRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component("ADEPT")
public class AdeptVariantStrategy implements VariantStrategy {

    private final PerkRepository perkRepo;

    private static final List<String> GRADE_PROGRESSION = Arrays.asList(
            "ASH_IV", "ASH_III", "ASH_II", "ASH_I",
            "BRONZE_IV", "BRONZE_III", "BRONZE_II", "BRONZE_I",
            "SILVER_IV", "SILVER_III", "SILVER_II", "SILVER_I",
            "GOLD_IV", "GOLD_III", "GOLD_II", "GOLD_I",
            "IRIDESCENT_IV", "IRIDESCENT_III", "IRIDESCENT_II", "IRIDESCENT_I"
    );

    public AdeptVariantStrategy(PerkRepository perkRepo) {
        this.perkRepo = perkRepo;
    }

    private int getMaxPipsForGrade(String grade) {
        if (grade.startsWith("ASH")) return 3;
        if (grade.startsWith("BRONZE")) return 4;
        return 5;
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
    }

    // --- STEP 3: APPLY RESULTS & ENFORCE LOADOUT ---
    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {

        // 1. ENFORCE ADEPT PERKS ONLY (Max 3)
        if (request.perkIds() != null && request.perkIds().size() > 3) {
            throw new GameRuleViolationException("Adept variant only allows a maximum of 3 perks.");
        }

        if (request.perkIds() != null && !request.perkIds().isEmpty()) {
            List<Perk> equippedPerks = perkRepo.findAllById(request.perkIds());
            for (Perk perk : equippedPerks) {
                if (perk.getKiller() == null || !perk.getKiller().getId().equals(killerRoster.getKiller().getId())) {
                    throw new GameRuleViolationException("You can only equip the unique Adept perks belonging to " + killerRoster.getKiller().getName() + ".");
                }
            }
        }

        // 2. ENFORCE ADD-ON RESTRICTIONS (Allowed in Ash, locked in Bronze+)
        String currentGrade = season.getCurrentGrade() != null ? season.getCurrentGrade().name() : "ASH_IV";
        boolean isAshGrade = currentGrade.startsWith("ASH");

        if (!isAshGrade && request.addOnIds() != null && !request.addOnIds().isEmpty()) {
            throw new GameRuleViolationException("Add-ons are strictly forbidden once you reach Bronze grade in the Adept variant.");
        }

        // 3. PERMADEATH (Gate Escape = Dead)
        boolean gateEscape = request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.ESCAPED);
        Map<String, Object> state = season.getVariantState();

        if (gateEscape) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);

            // Find the next available killer
            SeasonRoster nextAvailable = season.getRosters().stream()
                    .filter(r -> r.getStatus() != SeasonRoster.RosterStatus.DEAD && !r.getId().equals(killerRoster.getId()))
                    .findFirst()
                    .orElse(null);

            if (nextAvailable != null) {
                state.put("lastPlayedKillerId", nextAvailable.getKiller().getId().toString());
            } else {
                state.put("lastPlayedKillerId", null);
            }
        } else {
            state.put("lastPlayedKillerId", killerRoster.getKiller().getId().toString());
        }

        // 4. ATTACH SURVIVORS
        List<TrialSurvivor> trialSurvivors = request.survivorOutcomes().stream().map(outcome -> {
            TrialSurvivor survivor = new TrialSurvivor();
            survivor.setTrial(trial);
            survivor.setOutcome(outcome);
            return survivor;
        }).collect(Collectors.toList());
        trial.setSurvivors(trialSurvivors);

        season.setVariantState(state);

        // 5. PIP MATH
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
