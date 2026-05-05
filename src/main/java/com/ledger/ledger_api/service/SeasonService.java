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

import java.time.LocalDateTime;
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

    private final Map<String, VariantStrategy> strategies;

    public SeasonService(SeasonRepository seasonRepo, PlayerRepository playerRepo,
                         SeasonStatsRepository statsRepo, SeasonRosterRepository rosterRepo,
                         KillerRepository killerRepo, Map<String, VariantStrategy> strategies) {
        this.seasonRepo = seasonRepo;
        this.playerRepo = playerRepo;
        this.statsRepo = statsRepo;
        this.rosterRepo = rosterRepo;
        this.killerRepo = killerRepo;
        this.strategies = strategies;
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

    @Transactional(readOnly = true)
    public SeasonDetailsResponse getActiveSeasonDetails(UUID playerId) {
        Season season = seasonRepo.findByPlayerIdAndStatus(playerId, Season.SeasonStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active season found."));

        SeasonStats stats = statsRepo.findById(season.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Stats not found for season."));

        // Map the database entities to the DTO we defined earlier
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

        return new SeasonDetailsResponse(
                season.getId(), season.getVariantType(), season.getStatus(),
                season.getStartDate(), season.getEndDate(), season.getCurrentGrade(),
                season.getCurrentPips(), season.getVariantState(), statsDto, rosterDtos
        );
    }

    // --- VARIANT HISTORY METHODS ---

    @Transactional(readOnly = true)
    public List<Season> getSeasonsByVariant(UUID playerId, String variantTypeString) {
        // Convert the URL string (e.g., "STANDARD") into your Java Enum
        Season.VariantType type = Season.VariantType.valueOf(variantTypeString.toUpperCase());

        // Fetch all seasons for this specific variant, newest first
        return seasonRepo.findAllByPlayerIdAndVariantTypeOrderByStartDateDesc(playerId, type);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVariantStats(UUID playerId, String variantTypeString) {
        Season.VariantType type = Season.VariantType.valueOf(variantTypeString.toUpperCase());
        List<Season> variantSeasons = seasonRepo.findAllByPlayerIdAndVariantTypeOrderByStartDateDesc(playerId, type);

        int totalTrials = 0;
        double totalKillRateWeight = 0.0;

        // Loop through all seasons of this variant to aggregate the stats
        for (Season season : variantSeasons) {
            SeasonStats stats = statsRepo.findById(season.getId()).orElse(null);

            if (stats != null && stats.getMatchesPlayed() > 0) {
                totalTrials += stats.getMatchesPlayed();
                // Multiply the kill rate by the matches played to get a weighted calculation
                totalKillRateWeight += (stats.getKillRate() * stats.getMatchesPlayed());
            }
        }

        // Calculate the true overall kill rate across all seasons
        double overallKillRate = totalTrials > 0 ? (totalKillRateWeight / totalTrials) : 0.0;

        // Return a Map so Jackson automatically converts it into a JSON object
        // Notice we use "trialsPlayed" as the key to perfectly match your React frontend!
        return Map.of(
                "trialsPlayed", totalTrials,
                "killRate", Math.round(overallKillRate * 10.0) / 10.0 // Rounds to 1 decimal place
        );
    }

    // Sell Killer logic for Blood Money & Afterburn
    @Transactional
    public Season sellKiller(UUID playerId, UUID seasonId, Long killerId) {
        // 1. Fetch and validate season ownership
        Season season = seasonRepo.findById(seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("Season not found"));

        if (!season.getPlayer().getId().equals(playerId)) {
            throw new IllegalStateException("You do not have permission to modify this season.");
        }

        // 2. Ensure this is actually a Blood Money season
        if (season.getVariantType() != Season.VariantType.BLOOD_MONEY) {
            throw new IllegalStateException("You can only sell killers in the Blood Money variant.");
        }

        // 3. Find the specific killer in this season's roster
        SeasonRoster rosterEntry = season.getRosters().stream()
                .filter(r -> r.getKiller().getId().equals(killerId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Killer not found in this season's roster."));

        // 4. Validate the killer can be sold
        if (rosterEntry.getStatus() == SeasonRoster.RosterStatus.SOLD) {
            throw new IllegalStateException("This killer has already been sold.");
        }
        if (rosterEntry.getStatus() == SeasonRoster.RosterStatus.DEAD) {
            throw new IllegalStateException("You cannot sell a dead killer.");
        }

        // 5. Update the financial state
        Map<String, Object> state = season.getVariantState();
        int currentBalance = (int) state.getOrDefault("balance", 0);
        int sellPrice = rosterEntry.getKiller().getCost();

        state.put("balance", currentBalance + sellPrice);
        season.setVariantState(state);

        // 6. Mark as SOLD
        rosterEntry.setStatus(SeasonRoster.RosterStatus.SOLD);

        // 7. Save the changes
        rosterRepo.save(rosterEntry);
        return seasonRepo.save(season);
    }

    @Transactional
    public Season completeSeason(UUID playerId, UUID seasonId) {
        // 1. Find the season
        Season season = seasonRepo.findById(seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("Season not found"));

        // 2. Verify the player owns this season so they can't end someone else's game
        if (!season.getPlayer().getId().equals(playerId)) {
            throw new IllegalStateException("You do not have permission to modify this season.");
        }

        // 3. Flip the status
        season.setStatus(Season.SeasonStatus.COMPLETED);

        // 4. Save and return
        return seasonRepo.save(season);
    }
}
