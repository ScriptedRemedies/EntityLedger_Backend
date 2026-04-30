package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import com.ledger.ledger_api.repository.PerkRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("ADEPT")
public class AdeptVariantStrategy implements VariantStrategy {

    private final PerkRepository perkRepo;

    public AdeptVariantStrategy(PerkRepository perkRepo) {
        this.perkRepo = perkRepo;
    }

    // --- STEP 1: INITIALIZE ---
    @Override
    public void initializeSeasonState(Season season, Map<String, Object> requestConfig) {
        // Adept doesn't require extra tracking state, but we must initialize a blank map
        season.setVariantState(new HashMap<>());
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

        // 1. ENFORCE ADEPT PERKS ONLY
        if (request.perkIds() != null && request.perkIds().size() > 3) {
            throw new GameRuleViolationException("Adept variant only allows a maximum of 3 perks.");
        }

        if (request.perkIds() != null && !request.perkIds().isEmpty()) {
            List<Perk> equippedPerks = perkRepo.findAllById(request.perkIds());
            for (Perk perk : equippedPerks) {
                // Ensure the perk belongs to the exact killer being played
                if (perk.getKiller() == null || !perk.getKiller().getId().equals(killerRoster.getKiller().getId())) {
                    throw new GameRuleViolationException("You can only equip the unique Adept perks belonging to " + killerRoster.getKiller().getName() + ".");
                }
            }
        }

        // 2. ENFORCE ADD-ON RESTRICTIONS (Allowed in Ash, locked in Bronze+)
        // Assuming your Grade enum values look like "ASH_IV", "BRONZE_IV", etc.
        boolean isAshGrade = season.getCurrentGrade() != null && season.getCurrentGrade().name().startsWith("ASH");

        if (!isAshGrade) {
            if (request.addOnIds() != null && !request.addOnIds().isEmpty()) {
                throw new GameRuleViolationException("Add-ons are strictly forbidden once you reach Bronze grade in the Adept variant.");
            }
        }

        // 3. PERMADEATH (Gate Escape = Dead)
        if (request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.ESCAPED)) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        }
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
