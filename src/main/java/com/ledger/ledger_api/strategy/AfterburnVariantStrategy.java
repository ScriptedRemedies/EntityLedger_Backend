package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import com.ledger.ledger_api.repository.AddOnRepository;
import com.ledger.ledger_api.repository.PerkRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("AFTERBURN")
public class AfterburnVariantStrategy implements VariantStrategy {

    private final PerkRepository perkRepo;
    private final AddOnRepository addOnRepo;

    public AfterburnVariantStrategy(PerkRepository perkRepo, AddOnRepository addOnRepo) {
        this.perkRepo = perkRepo;
        this.addOnRepo = addOnRepo;
    }

    @Override
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer has been eliminated and cannot be played.");
        }
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.SOLD) {
            throw new GameRuleViolationException("This killer was sold and cannot be played.");
        }

        Map<String, Object> state = season.getVariantState();
        Integer balance = (Integer) state.getOrDefault("balance", 0);

        if (balance < 0) {
            throw new GameRuleViolationException("Your Afterburn funds are negative. You cannot start a trial until your debt is cleared.");
        }
    }

    @Override
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();
        Integer balance = (Integer) state.getOrDefault("balance", 0);

        boolean hatchEscapeOccurred = request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE);

        // Apply Permadeath
        if (hatchEscapeOccurred) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        }

        // Calculate Loadout Costs
        int totalCost = 0;

        if (request.perkIds() != null && !request.perkIds().isEmpty()) {
            List<Perk> equippedPerks = perkRepo.findAllById(request.perkIds());
            totalCost += equippedPerks.stream().mapToInt(Perk::getCost).sum();
        }

        if (request.addOnIds() != null && !request.addOnIds().isEmpty()) {
            List<AddOn> equippedAddOns = addOnRepo.findAllById(request.addOnIds());
            totalCost += equippedAddOns.stream().mapToInt(AddOn::getCost).sum();
        }

        // Calculate Trial Income and apply net change
        int trialIncome = calculateAfterburnIncome(request, trial.getPipProgression());
        int newBalance = balance + trialIncome - totalCost;

        state.put("balance", newBalance);
        season.setVariantState(state);
    }

    private int calculateAfterburnIncome(TrialSubmitRequest request, int pips) {
        int income = 0;

        // Example logic: Adjust these multipliers to fit your Afterburn rules!
        if (pips > 0) {
            income += (pips * 3); // Maybe Afterburn rewards pips slightly higher?
        }

        long kills = request.survivorOutcomes().stream()
                .filter(o -> o == TrialSurvivor.SurvivorOutcome.KILLED || o == TrialSurvivor.SurvivorOutcome.SACRIFICED)
                .count();
        income += (kills * 2);

        // Optional Afterburn rule: Bonus payout for a perfect 4K
        if (kills == 4) {
            income += 5;
        }

        return income;
    }
}
