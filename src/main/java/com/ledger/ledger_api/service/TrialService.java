package com.ledger.ledger_api.service;

// PURPOSE: Handles logging matches and calculating pip progression

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.dto.TrialSummaryResponse;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.event.TrialCompletedEvent;
import com.ledger.ledger_api.exception.GameRuleViolationException;
import com.ledger.ledger_api.exception.ResourceNotFoundException;
import com.ledger.ledger_api.repository.*;
import com.ledger.ledger_api.strategy.VariantStrategy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrialService {

    private final SeasonRepository seasonRepo;
    private final TrialRepository trialRepo;
    private final SeasonRosterRepository rosterRepo;
    private final KillerRepository killerRepo;
    private final ApplicationEventPublisher eventPublisher;

    // Spring automatically injects all your strategy files into this map!
    private final Map<String, VariantStrategy> strategies;

    public TrialService(
            SeasonRepository seasonRepo, TrialRepository trialRepo,
            SeasonRosterRepository rosterRepo, KillerRepository killerRepo,
            ApplicationEventPublisher eventPublisher, Map<String, VariantStrategy> strategies) {
        this.seasonRepo = seasonRepo;
        this.trialRepo = trialRepo;
        this.rosterRepo = rosterRepo;
        this.killerRepo = killerRepo;
        this.eventPublisher = eventPublisher;
        this.strategies = strategies;
    }

    @Transactional
    public TrialSummaryResponse submitTrial(TrialSubmitRequest request, UUID playerId) {
        // 1. Fetch the active season
        Season season = seasonRepo.findByPlayerIdAndStatus(playerId, Season.SeasonStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active season found for this player."));

        // 2. Fetch the specific killer's roster state for this season
        SeasonRoster killerRoster = rosterRepo.findBySeasonIdAndKillerId(season.getId(), request.killerId())
                .orElseThrow(() -> new ResourceNotFoundException("Killer not found in active season roster."));

        // 3. Fetch the correct ruleset based on the season's variant enum
        VariantStrategy strategy = strategies.get(season.getVariantType().name());
        if (strategy == null) {
            throw new IllegalStateException("Strategy not implemented for " + season.getVariantType());
        }

        // 4. Validate they are allowed to play this match (Checks dead/cooldown/funds)
        strategy.validateTrialStart(season, killerRoster);

        // 5. Build the Trial entity
        Trial trial = new Trial();
        trial.setSeason(season);
        trial.setKiller(killerRoster.getKiller());
        trial.setTrialNumber(trialRepo.countBySeasonId(season.getId()) + 1);
        trial.setPipProgression(request.pipProgression());

        // TODO: In a production app, you would fetch Perks and AddOns from their repos here using request.perkIds()
        // and attach them to the trial entity. For brevity, we assume that happens here.

        // 6. Apply rules and consequences (killing characters, deducting funds, etc.)
        strategy.applyTrialResults(season, killerRoster, trial, request);

        // 7. Save everything to the database
        // Because of @Transactional, if anything fails, none of this saves, preventing corrupted data.
        trialRepo.save(trial);
        rosterRepo.save(killerRoster);
        seasonRepo.save(season);

        // 8. Publish the event to update the stats in the background without slowing down the UI
        eventPublisher.publishEvent(new TrialCompletedEvent(season.getId()));

        // 9. Return the formatted DTO back to the controller
        return new TrialSummaryResponse(
                trial.getId(),
                trial.getTrialNumber(),
                trial.getKiller().getName(),
                trial.getPipProgression(),
                Collections.emptyList(), // Placeholder for perk names
                Collections.emptyList(), // Placeholder for addon names
                request.survivorOutcomes().stream().map(Enum::name).collect(Collectors.toList())
        );
    }

    @Transactional(readOnly = true)
    public List<Trial> getTrialsBySeason(UUID seasonId) {
        // Fetches the list from the database
        return trialRepo.findAllBySeasonIdOrderByTrialNumberAsc(seasonId);
    }
}
