package com.ledger.ledger_api.strategy;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import com.ledger.ledger_api.exception.ResourceNotFoundException;
import com.ledger.ledger_api.repository.AddOnRepository;
import com.ledger.ledger_api.repository.KillerRepository;
import com.ledger.ledger_api.repository.PerkRepository;
import com.ledger.ledger_api.repository.SeasonRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("AFTERBURN")
public class AfterburnVariantStrategy implements VariantStrategy {

    private final PerkRepository perkRepo;
    private final AddOnRepository addOnRepo;
    private final KillerRepository killerRepo;
    private final SeasonRepository seasonRepo;

    public AfterburnVariantStrategy(PerkRepository perkRepo, AddOnRepository addOnRepo,
                                    KillerRepository killerRepo, SeasonRepository seasonRepo) {
        this.perkRepo = perkRepo;
        this.addOnRepo = addOnRepo;
        this.killerRepo = killerRepo;
        this.seasonRepo = seasonRepo;
    }

    // --- STEP 1: INITIALIZE ---
    @Override
    public void initializeSeasonState(Season season, Map<String, Object> requestConfig) {
        Map<String, Object> state = season.getVariantState();

        if (season.getInheritedSeasonId() == null) {
            throw new GameRuleViolationException("Afterburn requires an inherited Blood Money season.");
        }

        // Fetch the old season to extract the leftover funds
        Season oldSeason = seasonRepo.findById(season.getInheritedSeasonId())
                .orElseThrow(() -> new ResourceNotFoundException("Inherited season not found."));

        int leftoverBalance = (int) oldSeason.getVariantState().getOrDefault("balance", 0);
        state.put("balance", leftoverBalance);

        // Setup fresh trackers for the new run's cooldowns
        state.put("cooldowns", new HashMap<String, Integer>());
        state.put("consecutiveWins", 0);
        state.put("lastWonKillerId", null);

        season.setVariantState(state);
    }

    // --- STEP 2: VALIDATE ---
    @Override
    @SuppressWarnings("unchecked")
    public void validateTrialStart(Season season, SeasonRoster killerRoster) {
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new GameRuleViolationException("This killer is dead and cannot be played.");
        }
        if (killerRoster.getStatus() == SeasonRoster.RosterStatus.SOLD) {
            throw new GameRuleViolationException("This killer was sold and cannot be played.");
        }

        Map<String, Object> state = season.getVariantState();
        int balance = (int) state.getOrDefault("balance", 0);

        // Check Cooldowns (Bypass if they only have 1 killer left)
        long availableKillersCount = season.getRosters().stream()
                .filter(r -> r.getStatus() == SeasonRoster.RosterStatus.AVAILABLE)
                .count();

        if (availableKillersCount > 1) {
            Map<String, Integer> cooldowns = (Map<String, Integer>) state.get("cooldowns");
            String killerIdStr = killerRoster.getKiller().getId().toString();

            if (cooldowns != null && cooldowns.getOrDefault(killerIdStr, 0) > 0) {
                throw new GameRuleViolationException("This killer is on cooldown for " + cooldowns.get(killerIdStr) + " more trial(s).");
            }
        }

        // Financial Validation
        int lowestKillerCost = killerRepo.findAll().stream().mapToInt(Killer::getCost).min().orElse(0);

        if (balance < 0 || balance < lowestKillerCost) {
            throw new GameRuleViolationException("Negative balance or insufficient funds. You must sell killers to reach at least $" + lowestKillerCost + ".");
        }

        if (balance < killerRoster.getKiller().getCost()) {
            throw new GameRuleViolationException("You cannot afford to play " + killerRoster.getKiller().getName() + ".");
        }
    }

    // --- APPLY RESULTS ---
    @Override
    @SuppressWarnings("unchecked")
    public void applyTrialResults(Season season, SeasonRoster killerRoster, Trial trial, TrialSubmitRequest request) {
        Map<String, Object> state = season.getVariantState();
        int balance = (int) state.getOrDefault("balance", 0);
        String currentKillerId = killerRoster.getKiller().getId().toString();

        // 1. PERMADEATH
        boolean gateEscape = request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.ESCAPED);
        if (gateEscape) {
            killerRoster.setStatus(SeasonRoster.RosterStatus.DEAD);
        }

        // 2. CALCULATE COSTS
        int totalCost = killerRoster.getKiller().getCost();

        if (request.perkIds() != null && !request.perkIds().isEmpty()) {
            List<Perk> equippedPerks = perkRepo.findAllById(request.perkIds());
            totalCost += equippedPerks.stream().mapToInt(Perk::getCost).sum();
        }

        if (request.addOnIds() != null && !request.addOnIds().isEmpty()) {
            List<AddOn> equippedAddOns = addOnRepo.findAllById(request.addOnIds());
            totalCost += equippedAddOns.stream().mapToInt(AddOn::getCost).sum();
        }

        // 3. CALCULATE INCOME & PENALTIES
        int trialIncome = 0;
        if (request.kills() == 4 && request.gensLeft() == 5) trialIncome += 5;
        if (request.kills() == 4 && request.gensLeft() == 4) trialIncome += 4;
        if (request.closedHatch()) trialIncome += 2;

        if (request.genBeforeHook()) trialIncome -= 3;
        if (request.lastGenCompleted()) trialIncome -= 2;
        if (request.gateOpened()) trialIncome -= 5;
        if (request.survivorOutcomes().contains(TrialSurvivor.SurvivorOutcome.HATCH_ESCAPE)) trialIncome -= 2;

        int newBalance = balance + trialIncome - totalCost;
        state.put("balance", newBalance);

        // 4. COOLDOWN MANAGEMENT
        long kills = request.survivorOutcomes().stream()
                .filter(o -> o == TrialSurvivor.SurvivorOutcome.KILLED || o == TrialSurvivor.SurvivorOutcome.SACRIFICED || o == TrialSurvivor.SurvivorOutcome.DISCONNECTED)
                .count();
        boolean isWin = (kills >= 3) && !gateEscape;

        Map<String, Integer> cooldowns = (Map<String, Integer>) state.get("cooldowns");

        cooldowns.entrySet().removeIf(entry -> {
            int newCooldown = entry.getValue() - 1;
            entry.setValue(newCooldown);
            return newCooldown <= 0;
        });

        String lastWonId = (String) state.get("lastWonKillerId");
        int consecutiveWins = (int) state.get("consecutiveWins");

        if (isWin) {
            if (currentKillerId.equals(lastWonId)) {
                consecutiveWins++;
                if (consecutiveWins >= 2) {
                    cooldowns.put(currentKillerId, 2);
                    consecutiveWins = 0;
                    lastWonId = null;
                }
            } else {
                lastWonId = currentKillerId;
                consecutiveWins = 1;
            }
        } else {
            consecutiveWins = 0;
            lastWonId = null;
        }

        state.put("cooldowns", cooldowns);
        state.put("consecutiveWins", consecutiveWins);
        state.put("lastWonKillerId", lastWonId);
        season.setVariantState(state);
    }

    // --- STEP 4: END GAME ---
    @Override
    public boolean isSeasonOver(Season season) {
        Map<String, Object> state = season.getVariantState();
        int balance = (int) state.getOrDefault("balance", 0);

        boolean allKillersGone = season.getRosters().stream()
                .allMatch(r -> r.getStatus() == SeasonRoster.RosterStatus.DEAD || r.getStatus() == SeasonRoster.RosterStatus.SOLD);

        if (allKillersGone) return true;

        if (season.getCurrentGrade().name().equals("IRIDESCENT_1")) {
            if (balance >= 0) return true;

            boolean canStillSell = season.getRosters().stream()
                    .anyMatch(r -> r.getStatus() == SeasonRoster.RosterStatus.AVAILABLE);

            return !canStillSell;
        }

        return false;
    }
}
