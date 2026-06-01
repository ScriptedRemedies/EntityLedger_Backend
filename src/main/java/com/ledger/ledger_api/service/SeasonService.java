package com.ledger.ledger_api.service;

import com.ledger.ledger_api.dto.SeasonCreateRequest;
import com.ledger.ledger_api.dto.SeasonDetailsResponse;
import com.ledger.ledger_api.dto.VariantStatsResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
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

        strategy.initializeSeasonState(season, new java.util.HashMap<>());

        season = seasonRepo.save(season);

        SeasonStats stats = new SeasonStats();
        stats.setSeason(season);
        statsRepo.save(stats);

        // 4. Generate or Inherit the starting roster
        if (season.getInheritedSeasonId() != null) {
            // AFTERBURN: Copy ONLY the AVAILABLE killers from the previous season
            Season inheritedSeason = seasonRepo.findById(season.getInheritedSeasonId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inherited season not found"));

            List <String> deadAndSoldNames = new ArrayList<>();

            for (SeasonRoster oldRoster : inheritedSeason.getRosters()) {
                // If they are DEAD or SOLD, they do not make it into the new season
                if (oldRoster.getStatus() == SeasonRoster.RosterStatus.AVAILABLE) {
                    SeasonRoster rosterEntry = new SeasonRoster();
                    rosterEntry.setSeason(season);
                    rosterEntry.setKiller(oldRoster.getKiller());
                    rosterEntry.setStatus(SeasonRoster.RosterStatus.AVAILABLE);
                    rosterRepo.save(rosterEntry);
                } else {
                    deadAndSoldNames.add(oldRoster.getKiller().getName());
                }
            }

            Map<String, Object> state = season.getVariantState();
            state.put("deadAndSoldKillerNames", deadAndSoldNames);
            season.setVariantState(state);

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

            // FIXED: Stamp the end date during Lazy Evaluation as well!
            season.setEndDate(LocalDateTime.now());

            seasonRepo.saveAndFlush(season);

            // Throw exception so the frontend kicks the user back to the "Start Challenge" screen
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
    public VariantStatsResponse getVariantStats(UUID playerId, String variantTypeString) {
        Season.VariantType type = Season.VariantType.valueOf(variantTypeString.toUpperCase());
        List<Season> variantSeasons = seasonRepo.findAllByPlayerIdAndVariantTypeOrderByStartDateDesc(playerId, type);

        // Gather every trial from every season of this variant
        List<Trial> allTrialsForVariant = new ArrayList<>();
        for (Season season : variantSeasons) {
            allTrialsForVariant.addAll(trialRepo.findAllBySeasonIdOrderByTrialNumberAsc(season.getId()));
        }

        // Crunch the numbers!
        return calculateVariantStats(allTrialsForVariant);
    }
    private VariantStatsResponse calculateVariantStats(List<Trial> trials) {
        int matches = trials.size();
        if (matches == 0) {
            return new VariantStatsResponse(0, 0.0, 0.0, 0, 0.0, 0.0, 0, List.of(), List.of());
        }

        int totalKills = 0;
        int fourKCount = 0;
        int pipSum = 0;
        int lossCount = 0;
        int hatchCount = 0;
        int twoToThreeKillsWithGates = 0;

        Map<String, Integer> perkCounts = new HashMap<>();
        Map<String, Integer> iriEmblemCounts = new HashMap<>();

        // Ensure these keys always exist so the UI maps them correctly
        iriEmblemCounts.put("CHASER", 0);
        iriEmblemCounts.put("DEVOUT", 0);
        iriEmblemCounts.put("GATEKEEPER", 0);
        iriEmblemCounts.put("MALICIOUS", 0);

        for (Trial t : trials) {
            pipSum += t.getPipProgression() != null ? t.getPipProgression() : 0;

            int killsInTrial = 0;
            boolean gateEscape = false;
            boolean hatchEscape = false;

            for (TrialSurvivor s : t.getSurvivors()) {
                String outcome = s.getOutcome().name();
                if (outcome.equals("KILLED") || outcome.equals("SACRIFICED")) {
                    killsInTrial++;
                } else if (outcome.equals("ESCAPED")) {
                    gateEscape = true;
                } else if (outcome.equals("HATCH_ESCAPE")) {
                    hatchEscape = true;
                }
            }

            totalKills += killsInTrial;

            if (killsInTrial == 4) fourKCount++;
            if (gateEscape) lossCount++; // Loss = Entity Displeased / Survivor escaped via gate
            if (hatchEscape) hatchCount++;
            if ((killsInTrial == 2 || killsInTrial == 3) && gateEscape) twoToThreeKillsWithGates++;

            // Tally Perks
            t.getPerks().forEach(p -> perkCounts.put(p.getName(), perkCounts.getOrDefault(p.getName(), 0) + 1));

            // Tally Iridescent Emblems
            t.getEmblems().stream()
                    .filter(e -> e.getType().name().equals("IRIDESCENT"))
                    .forEach(e -> iriEmblemCounts.put(e.getCategory().name(), iriEmblemCounts.get(e.getCategory().name()) + 1));
        }

        // Helper to format percentages
        var formatPct = (java.util.function.BiFunction<Integer, Integer, Double>) (count, total) ->
                Math.round(((double) count / total) * 1000.0) / 10.0;

        // Map Top 4 Perks
        List<VariantStatsResponse.PerkStat> topPerks = perkCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(4)
                .map(e -> new VariantStatsResponse.PerkStat(e.getKey(), formatPct.apply(e.getValue(), matches)))
                .toList();

        // Map Emblems
        List<VariantStatsResponse.EmblemStat> emblems = iriEmblemCounts.entrySet().stream()
                .map(e -> new VariantStatsResponse.EmblemStat(e.getKey(), formatPct.apply(e.getValue(), matches)))
                .toList();

        return new VariantStatsResponse(
                matches,
                formatPct.apply(totalKills, matches * 4), // Kill rate is based on total survivors faced
                formatPct.apply(fourKCount, matches),
                pipSum,
                formatPct.apply(lossCount, matches),
                formatPct.apply(hatchCount, matches),
                twoToThreeKillsWithGates,
                topPerks,
                emblems
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

        // Extract the state map to update our ledger
        Map<String, Object> state = season.getVariantState();
        int currentBalance = (int) state.getOrDefault("balance", 0);
        int sellPrice = rosterEntry.getKiller().getCost();

        // 1. Process the financial transaction
        state.put("balance", currentBalance + sellPrice);

        // 2. Update the specific roster item
        rosterEntry.setStatus(SeasonRoster.RosterStatus.SOLD);
        rosterRepo.save(rosterEntry);

        season = seasonRepo.findById(seasonId).get();

        // 3. --- COOLDOWN WAIVER CHECK ---
        long remainingAlive = season.getRosters().stream()
                .filter(r -> r.getStatus() != SeasonRoster.RosterStatus.DEAD && r.getStatus() != SeasonRoster.RosterStatus.SOLD)
                .count();

        // If the sale dropped them to their final killer, wipe the active cooldown
        if (remainingAlive <= 1) {
            state.put("cooldownKillerId", null);
            state.put("cooldownTrialsLeft", 0);
        }

        int newBalance = currentBalance + sellPrice;
        boolean canAffordAnyone = season.getRosters().stream()
                .filter(r -> r.getStatus() != SeasonRoster.RosterStatus.DEAD && r.getStatus() != SeasonRoster.RosterStatus.SOLD)
                .anyMatch(r -> r.getKiller().getCost() <= newBalance);

        if (remainingAlive == 0) {
            season.setStatus(Season.SeasonStatus.FAILED_ROSTER); // The run is dead
            season.setEndDate(LocalDateTime.now());
        }

        // 4. Save the fully updated state back to the season
        season.setVariantState(state);

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

    @Transactional
    public Season failSeason(UUID playerId, UUID seasonId) {
        Season season = seasonRepo.findById(seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("Season not found"));

        if (!season.getPlayer().getId().equals(playerId)) {
            throw new IllegalStateException("You do not have permission to modify this season.");
        }

        season.setStatus(Season.SeasonStatus.FAILED_TIME);
        season.setEndDate(LocalDateTime.now());

        // Specifically for Iron Man, ensure the run state is marked dead
        Map<String, Object> state = season.getVariantState();
        if (season.getVariantType() == Season.VariantType.IRON_MAN) {
            state.put("runDead", true);
            season.setVariantState(state);
        }

        return seasonRepo.save(season);
    }
}
