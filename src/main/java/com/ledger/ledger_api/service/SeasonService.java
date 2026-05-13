package com.ledger.ledger_api.service;

import com.ledger.ledger_api.dto.SeasonCreateRequest;
import com.ledger.ledger_api.dto.SeasonDetailsResponse;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.exception.ActiveSeasonExistsException;
import com.ledger.ledger_api.exception.ResourceNotFoundException;
import com.ledger.ledger_api.repository.*;
import com.ledger.ledger_api.strategy.VariantStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SeasonService {

    private final SeasonRepository seasonRepo;
    private final PlayerRepository playerRepo;
    private final SeasonStatsRepository statsRepo;
    private final SeasonRosterRepository rosterRepo;
    private final KillerRepository killerRepo;
    private final TrialRepository trialRepo;

    private final Map<String, VariantStrategy> strategies;

    public SeasonService(SeasonRepository seasonRepo, PlayerRepository playerRepo,
                         SeasonStatsRepository statsRepo, SeasonRosterRepository rosterRepo,
                         KillerRepository killerRepo, Map<String, VariantStrategy> strategies,
                         TrialRepository trialRepo) {
        this.seasonRepo = seasonRepo;
        this.playerRepo = playerRepo;
        this.statsRepo = statsRepo;
        this.rosterRepo = rosterRepo;
        this.killerRepo = killerRepo;
        this.strategies = strategies;
        this.trialRepo = trialRepo;
    }

    @Transactional
    public Season startNewSeason(UUID playerId, SeasonCreateRequest request) {
        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));

        // 1. Prevent multiple active seasons
        if (seasonRepo.findByPlayerIdAndStatus(playerId, Season.SeasonStatus.ACTIVE).isPresent()) {
            throw new ActiveSeasonExistsException("You must finish or fail your current season before starting a new one.");
        }

        // Fetch the specific strategy based on the requested variant
        VariantStrategy strategy = strategies.get(request.variantType().name());
        if (strategy == null) {
            throw new IllegalStateException("Strategy not implemented for " + request.variantType());
        }

        // 2. Create the base Season
        Season season = new Season();
        season.setPlayer(player);
        season.setVariantType(request.variantType());
        season.setStatus(Season.SeasonStatus.ACTIVE);
        season.setStartDate(LocalDateTime.now());
        season.setCurrentGrade(request.startingGrade());
        season.setInheritedSeasonId(request.inheritedSeasonId());

        // Initialize a blank state map before handing it to the strategy
        season.setVariantState(new java.util.HashMap<>());

        season = seasonRepo.save(season);

        SeasonStats stats = new SeasonStats();
        stats.setSeason(season);
        statsRepo.save(stats);

        // 4. Generate or Inherit the starting roster
        if (season.getInheritedSeasonId() != null) {
            // AFTERBURN: Copy the exact roster statuses from the previous season
            Season inheritedSeason = seasonRepo.findById(season.getInheritedSeasonId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inherited season not found"));

            for (SeasonRoster oldRoster : inheritedSeason.getRosters()) {
                SeasonRoster rosterEntry = new SeasonRoster();
                rosterEntry.setSeason(season);
                rosterEntry.setKiller(oldRoster.getKiller());
                rosterEntry.setStatus(oldRoster.getStatus()); // Copies DEAD, SOLD, or AVAILABLE
                rosterRepo.save(rosterEntry);
            }
        } else {
            // STANDARD/BLOOD MONEY/ETC: Fresh roster of all killers
            if (request.unlockedKillerIds() == null || request.unlockedKillerIds().isEmpty()) {
                throw new IllegalStateException("You must select at least one killer to start a trial.");
            }

            List<Killer> allKillers = killerRepo.findAllById(request.unlockedKillerIds());
            for (Killer killer : allKillers) {
                SeasonRoster rosterEntry = new SeasonRoster();
                rosterEntry.setSeason(season);
                rosterEntry.setKiller(killer);
                rosterEntry.setStatus(SeasonRoster.RosterStatus.AVAILABLE);
                rosterRepo.save(rosterEntry);
            }
        }

        return season;
    }

    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public SeasonDetailsResponse getActiveSeasonDetails(UUID playerId) {

        Season season = seasonRepo.findByPlayerIdAndStatus(playerId, Season.SeasonStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active season found."));

        LocalDate startDate = season.getStartDate().toLocalDate();
        LocalDate targetResetDate = startDate.withDayOfMonth(13);

        if (startDate.isAfter(targetResetDate) || startDate.isEqual(targetResetDate)) {
            targetResetDate = targetResetDate.plusMonths(1);
        }

        LocalDate today = LocalDate.now();

        if (today.isAfter(targetResetDate) || today.isEqual(targetResetDate)) {

            season.setStatus(Season.SeasonStatus.FAILED_TIME);

            // FORCE Hibernate to write to the database immediately before throwing the exception
            seasonRepo.saveAndFlush(season);

            throw new ResourceNotFoundException("Time ran out! Your season has failed.");
        }

        Integer daysLeft = (int) ChronoUnit.DAYS.between(today, targetResetDate);

        // --- FETCH THE REST OF THE DATA ---
        SeasonStats stats = statsRepo.findById(season.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Stats not found for season."));

        SeasonDetailsResponse.StatsDto statsDto = new SeasonDetailsResponse.StatsDto(
                stats.getMatchesPlayed(), stats.getKillRate(), stats.getFourKRate(),
                stats.getRosterSurvivalRate(), stats.getHatchEscapeRate(),
                stats.getGateEscapeRate(), stats.getTotalIridescentEmblems(),
                stats.getTopFourPerks(), stats.getTopFourKillers()
        );

        List<SeasonDetailsResponse.RosterItemDto> rosterDtos = rosterRepo.findBySeasonId(season.getId()).stream()
                .map(r -> new SeasonDetailsResponse.RosterItemDto(
                        r.getKiller().getId(), r.getKiller().getName(),
                        r.getStatus().name(), r.getKiller().getCost()))
                .collect(Collectors.toList());

        List<Trial> trials = trialRepo.findAllBySeasonIdOrderByTrialNumberAsc(season.getId());
        Killer displayKiller = null;

        if (trials != null && !trials.isEmpty()) {
            displayKiller = trials.get(trials.size() - 1).getKiller();
        } else {
            displayKiller = rosterRepo.findBySeasonId(season.getId()).stream()
                    .filter(r -> r.getStatus() == SeasonRoster.RosterStatus.AVAILABLE)
                    .map(SeasonRoster::getKiller)
                    .findFirst()
                    .orElse(null);
        }

        String characterName = displayKiller != null ? displayKiller.getName() : "The Entity";
        String characterImageUrl = displayKiller != null ?
                "/assets/Killer Portraits/" + displayKiller.getName() + ".png" :
                "/assets/placeholder-killer.png";

        return new SeasonDetailsResponse(
                season.getId(), season.getVariantType(), season.getStatus(),
                season.getStartDate(), season.getEndDate(), season.getCurrentGrade(),
                season.getCurrentPips(), season.getVariantState(), statsDto, rosterDtos,
                characterName, characterImageUrl, daysLeft
        );
    }

    // --- VARIANT HISTORY METHODS ---

    @Transactional(readOnly = true)
    public List<Season> getSeasonsByVariant(UUID playerId, String variantTypeString) {
        Season.VariantType type = Season.VariantType.valueOf(variantTypeString.toUpperCase());
        return seasonRepo.findAllByPlayerIdAndVariantTypeOrderByStartDateDesc(playerId, type);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVariantStats(UUID playerId, String variantTypeString) {
        Season.VariantType type = Season.VariantType.valueOf(variantTypeString.toUpperCase());
        List<Season> variantSeasons = seasonRepo.findAllByPlayerIdAndVariantTypeOrderByStartDateDesc(playerId, type);

        int totalTrials = 0;
        double totalKillRateWeight = 0.0;

        for (Season season : variantSeasons) {
            SeasonStats stats = statsRepo.findById(season.getId()).orElse(null);

            if (stats != null && stats.getMatchesPlayed() > 0) {
                totalTrials += stats.getMatchesPlayed();
                totalKillRateWeight += (stats.getKillRate() * stats.getMatchesPlayed());
            }
        }

        double overallKillRate = totalTrials > 0 ? (totalKillRateWeight / totalTrials) : 0.0;

        return Map.of(
                "trialsPlayed", totalTrials,
                "killRate", Math.round(overallKillRate * 10.0) / 10.0
        );
    }

    // Sell Killer logic for Blood Money & Afterburn
    @Transactional
    public Season sellKiller(UUID playerId, UUID seasonId, Long killerId) {
        Season season = seasonRepo.findById(seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("Season not found"));

        if (!season.getPlayer().getId().equals(playerId)) {
            throw new IllegalStateException("You do not have permission to modify this season.");
        }

        if (season.getVariantType() != Season.VariantType.BLOOD_MONEY) {
            throw new IllegalStateException("You can only sell killers in the Blood Money variant.");
        }

        SeasonRoster rosterEntry = season.getRosters().stream()
                .filter(r -> r.getKiller().getId().equals(killerId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Killer not found in this season's roster."));

        if (rosterEntry.getStatus() == SeasonRoster.RosterStatus.SOLD) {
            throw new IllegalStateException("This killer has already been sold.");
        }
        if (rosterEntry.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new IllegalStateException("You cannot sell a dead killer.");
        }

        Map<String, Object> state = season.getVariantState();
        int currentBalance = (int) state.getOrDefault("balance", 0);
        int sellPrice = rosterEntry.getKiller().getCost();

        state.put("balance", currentBalance + sellPrice);
        season.setVariantState(state);

        rosterEntry.setStatus(SeasonRoster.RosterStatus.SOLD);

        rosterRepo.save(rosterEntry);
        return seasonRepo.save(season);
    }

    @Transactional
    public Season completeSeason(UUID playerId, UUID seasonId) {
        Season season = seasonRepo.findById(seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("Season not found"));

        if (!season.getPlayer().getId().equals(playerId)) {
            throw new IllegalStateException("You do not have permission to modify this season.");
        }

        season.setStatus(Season.SeasonStatus.COMPLETED);

        return seasonRepo.save(season);
    }
}
