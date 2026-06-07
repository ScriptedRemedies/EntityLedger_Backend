package com.ledger.ledger_api.service;

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
import com.ledger.ledger_api.entity.Emblem;
import com.ledger.ledger_api.repository.EmblemRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrialService {

    private final SeasonRepository seasonRepo;
    private final TrialRepository trialRepo;
    private final SeasonRosterRepository rosterRepo;
    private final KillerRepository killerRepo;
    private final ApplicationEventPublisher eventPublisher;

    // 1. Inject the missing Repositories
    private final PerkRepository perkRepo;
    private final AddOnRepository addOnRepo;
    private final EmblemRepository emblemRepo;

    private final Map<String, VariantStrategy> strategies;

    public TrialService(
            SeasonRepository seasonRepo, TrialRepository trialRepo,
            SeasonRosterRepository rosterRepo, KillerRepository killerRepo,
            ApplicationEventPublisher eventPublisher, Map<String, VariantStrategy> strategies,
            PerkRepository perkRepo, AddOnRepository addOnRepo, EmblemRepository emblemRepo) {
        this.seasonRepo = seasonRepo;
        this.trialRepo = trialRepo;
        this.rosterRepo = rosterRepo;
        this.killerRepo = killerRepo;
        this.eventPublisher = eventPublisher;
        this.strategies = strategies;
        this.perkRepo = perkRepo;
        this.addOnRepo = addOnRepo;
        this.emblemRepo = emblemRepo;
    }

    @Transactional
    public TrialSummaryResponse submitTrial(TrialSubmitRequest request, UUID playerId) {
        Season season = seasonRepo.findByPlayerIdAndStatus(playerId, Season.SeasonStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active season found for this player."));

        SeasonRoster killerRoster = rosterRepo.findBySeasonIdAndKillerId(season.getId(), request.killerId())
                .orElseThrow(() -> new ResourceNotFoundException("Killer not found in active season roster."));

        VariantStrategy strategy = strategies.get(season.getVariantType().name());
        if (strategy == null) {
            throw new IllegalStateException("Strategy not implemented for " + season.getVariantType());
        }

        strategy.validateTrialStart(season, killerRoster);

        Trial trial = new Trial();
        trial.setSeason(season);
        trial.setKiller(killerRoster.getKiller());
        trial.setTrialNumber(trialRepo.countBySeasonId(season.getId()) + 1);
        trial.setPipProgression(request.pipProgression());

        // --- FETCH AND ATTACH ENTITIES ---

        // 2. Process Perks
        if (request.perkIds() != null && !request.perkIds().isEmpty()) {
            trial.setPerks(new HashSet<>(perkRepo.findAllById(request.perkIds())));
        }

        // 3. Process Add-ons
        if (request.addOnIds() != null && !request.addOnIds().isEmpty()) {
            trial.setAddOns(new HashSet<>(addOnRepo.findAllById(request.addOnIds())));
        }

        // 4. Process Emblems
        if (request.emblems() != null && !request.emblems().isEmpty()) {
            Set<Emblem> trialEmblems = request.emblems().stream().map(payload -> {
                // Map String category to Enum
                Emblem.EmblemCategory category = Emblem.EmblemCategory.valueOf(payload.category().toUpperCase());

                // Handle the naming difference: UI sends "PLATINUM", Database/Enum expects "IRIDESCENT"
                String safeQuality = payload.quality().toUpperCase().equals("PLATINUM")
                        ? "IRIDESCENT"
                        : payload.quality().toUpperCase();
                Emblem.EmblemType type = Emblem.EmblemType.valueOf(safeQuality);

                // Fetch from database and map
                return emblemRepo.findByCategoryAndType(category, type)
                        .orElseThrow(() -> new ResourceNotFoundException("Emblem not found: " + category + " " + type));
            }).collect(Collectors.toSet());

            trial.setEmblems(trialEmblems);
        }

        strategy.applyTrialResults(season, killerRoster, trial, request);

        // 1. Capture the exact status evaluated by the strategy
        Season.SeasonStatus terminalStatus = strategy.isSeasonOver(season);

        // 2. If it's not ACTIVE, the season is officially over.
        if (terminalStatus != Season.SeasonStatus.ACTIVE) {
            season.setStatus(terminalStatus);
            season.setEndDate(java.time.LocalDateTime.now()); // Set the official end timestamp
        }

        trialRepo.save(trial);
        rosterRepo.save(killerRoster);
        seasonRepo.save(season);

        eventPublisher.publishEvent(new TrialCompletedEvent(season.getId()));

        // 5. Build full DTO response for the UI
        return new TrialSummaryResponse(
                trial.getId(),
                trial.getTrialNumber(),
                trial.getKiller().getName(),
                trial.getKiller().getCost(),
                trial.getPipProgression(),
                trial.getResultingGrade() != null ? trial.getResultingGrade().name() : season.getCurrentGrade().name(),
                trial.getResultingPips() != null ? trial.getResultingPips() : season.getCurrentPips(),
                trial.getNetIncome() != null ? trial.getNetIncome() : 0,
                trial.getEarnedMulligan() != null ? trial.getEarnedMulligan() : false,
                trial.getBurnedMulligan() != null ? trial.getBurnedMulligan() : false,
                trial.getFlawlessTrial() != null ? trial.getFlawlessTrial() : false,
                trial.getRunningBalance() != null ? trial.getRunningBalance() : 0,
                trial.getUsedReRollToken() != null ? trial.getUsedReRollToken() : false,
                trial.getRemainingTokens() != null ? trial.getRemainingTokens() : 0,

                // Survivor Outcomes
                trial.getSurvivors().stream().map(s -> s.getOutcome().name()).collect(Collectors.toList()),

                // Emblems (Maps to icons)
                trial.getEmblems().stream().map(e -> new TrialSummaryResponse.EmblemDTO(
                        e.getCategory().name(),
                        "/assets/Emblems/" + e.getCategory().name().toLowerCase() + "_" + e.getType().name().toLowerCase() + ".png"
                )).collect(Collectors.toList()),

                // Perks (Maps to icons)
                trial.getPerks().stream().map(p -> new TrialSummaryResponse.PerkDTO(
                        p.getName(),
                        "/assets/Perks/" + p.getName() + ".png",
                        p.getCost()
                )).collect(Collectors.toList()),

                // Add-ons (Maps to icons, replacing % to match your file naming convention)
                trial.getAddOns().stream().map(a -> new TrialSummaryResponse.AddonDTO(
                        a.getName(),
                        "/assets/Addons/" + trial.getKiller().getName() + "/" + a.getName().replace("%", "") + ".png",
                        a.getCost()
                )).collect(Collectors.toList()),

                season.getStatus().name()
        );
    }

    @Transactional(readOnly = true)
    public List<Trial> getTrialsBySeason(UUID seasonId) {
        return trialRepo.findAllBySeasonIdOrderByTrialNumberAsc(seasonId);
    }
}
