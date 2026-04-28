package com.ledger.ledger_api.service;

// PURPOSE: Handles starting, fetching, and failing seasons

import com.ledger.ledger_api.dto.SeasonCreateRequest;
import com.ledger.ledger_api.dto.SeasonDetailsResponse;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.exception.ActiveSeasonExistsException;
import com.ledger.ledger_api.exception.ResourceNotFoundException;
import com.ledger.ledger_api.repository.*;
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

    public SeasonService(SeasonRepository seasonRepo, PlayerRepository playerRepo,
                         SeasonStatsRepository statsRepo, SeasonRosterRepository rosterRepo,
                         KillerRepository killerRepo) {
        this.seasonRepo = seasonRepo;
        this.playerRepo = playerRepo;
        this.statsRepo = statsRepo;
        this.rosterRepo = rosterRepo;
        this.killerRepo = killerRepo;
    }

    @Transactional
    public Season startNewSeason(UUID playerId, SeasonCreateRequest request) {
        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));

        // 1. Prevent multiple active seasons
        if (seasonRepo.findByPlayerIdAndStatus(playerId, Season.SeasonStatus.ACTIVE).isPresent()) {
            throw new ActiveSeasonExistsException("You must finish or fail your current season before starting a new one.");
        }

        // 2. Create the base Season
        Season season = new Season();
        season.setPlayer(player);
        season.setVariantType(request.variantType());
        season.setStatus(Season.SeasonStatus.ACTIVE);
        season.setStartDate(LocalDateTime.now());
        season.setCurrentGrade(request.startingGrade());
        season.setVariantState(request.variantSettings());
        season.setInheritedSeasonId(request.inheritedSeasonId());

        // Save to generate the UUID
        season = seasonRepo.save(season);

        // 3. Initialize blank Stats for this season
        SeasonStats stats = new SeasonStats();
        stats.setSeason(season);
        statsRepo.save(stats);

        // 4. Generate the starting roster (all 36+ killers)
        List<Killer> allKillers = killerRepo.findAll();
        for (Killer killer : allKillers) {
            SeasonRoster rosterEntry = new SeasonRoster();
            rosterEntry.setSeason(season);
            rosterEntry.setKiller(killer);
            rosterEntry.setStatus(SeasonRoster.RosterStatus.AVAILABLE);
            rosterRepo.save(rosterEntry);
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
}
