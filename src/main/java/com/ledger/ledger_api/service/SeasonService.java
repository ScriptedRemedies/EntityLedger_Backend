package com.ledger.ledger_api.service;

import com.ledger.ledger_api.dto.SeasonCreateRequest;
import com.ledger.ledger_api.dto.SeasonDetailsResponse;
import com.ledger.ledger_api.dto.VariantStatsResponse;
import com.ledger.ledger_api.dto.SeasonHistoryResponse;
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
                season.getCurrentPhase(),
                season.getStartDate(), season.getEndDate(), season.getCurrentGrade(),
                season.getCurrentPips(), season.getVariantState(), statsDto, rosterDtos,
                season.getDraftState(),
                characterName, characterImageUrl, daysLeft
        );
    }

    // --- VARIANT HISTORY METHODS ---

    @Transactional(readOnly = true)
    public List<SeasonHistoryResponse> getSeasonsByVariant(UUID playerId, String variantTypeString) {
        Season.VariantType type = Season.VariantType.valueOf(variantTypeString.toUpperCase());
        List<Season> seasons = seasonRepo.findAllByPlayerIdAndVariantTypeOrderByStartDateDesc(playerId, type);

        // Map the raw database entities to our safe, frontend-friendly DTO
        return seasons.stream().map(season -> {

            // 1. THE LAZY LOAD FIX: Eagerly fetch the rosters and map them to DTOs
            List<SeasonDetailsResponse.RosterItemDto> rosterDtos = rosterRepo.findBySeasonId(season.getId()).stream()
                    .map(r -> new SeasonDetailsResponse.RosterItemDto(
                            r.getKiller().getId(), r.getKiller().getName(),
                            r.getStatus().name(), r.getKiller().getCost()))
                    .collect(Collectors.toList());

            // 2. Identify the character for the background watermark
            String characterName = "The Entity";
            String characterImageUrl = "/assets/placeholder-killer.png";

            // Fallback to the first available killer to represent the season card
            var displayKiller = rosterDtos.stream()
                    .filter(r -> r.status().equals("AVAILABLE"))
                    .findFirst()
                    .orElse(null);

            if (displayKiller != null) {
                characterName = displayKiller.killerName();
                characterImageUrl = "/assets/Killer Portraits/" + displayKiller.killerName() + ".png";
            }

            // 3. Return the clean payload
            return new SeasonHistoryResponse(
                    season.getId(), season.getVariantType(), season.getStatus(),
                    season.getStartDate(), season.getEndDate(), season.getCurrentGrade(),
                    season.getCurrentPips(), rosterDtos, characterName, characterImageUrl
            );
        }).collect(Collectors.toList());
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

        return calculateVariantStats(variantSeasons, allTrialsForVariant);
    }

    private VariantStatsResponse calculateVariantStats(List<Season> seasons, List<Trial> trials) {
        int matches = trials.size();

        // 1. Safe Empty State Return
        if (matches == 0) {
            return new VariantStatsResponse(
                    0, 0.0, 0.0, 0, 0.0, 0.0, 0,
                    List.of(), List.of(), List.of(), List.of(),
                    new VariantStatsResponse.FinancialExtremes(
                            new VariantStatsResponse.TrialRecord(0, 0),
                            new VariantStatsResponse.TrialRecord(0, 0)
                    ), 0, 0, null, 0, 0, 0.0
            );
        }

        // 2. Base Tracking Variables
        int totalKills = 0, fourKCount = 0, pipSum = 0, lossCount = 0, hatchCount = 0, twoToThreeKillsWithGates = 0;
        int totalRevenue = 0, totalDebt = 0;
        int maxWinAmount = 0, maxWinTrialNum = 0;
        int maxLossAmount = 0, maxLossTrialNum = 0;
        int mulligansBurned = 0, flawlessCount = 0;
        int totalPerkCost = 0, totalPerksEquipped = 0;

        Map<String, Integer> perkCounts = new HashMap<>();
        Map<String, Integer> iriEmblemCounts = new HashMap<>();
        iriEmblemCounts.put("CHASER", 0); iriEmblemCounts.put("DEVOUT", 0);
        iriEmblemCounts.put("GATEKEEPER", 0); iriEmblemCounts.put("MALICIOUS", 0);

        // Tracker for Killer-specific awards and stats
        class KillerAgg {
            int matches = 0, kills = 0, pips = 0, fourKs = 0, losses = 0, gateEscapes = 0, hatchEscapes = 0;
        }
        Map<String, KillerAgg> killerStats = new HashMap<>();

        // 3. THE MASTER LOOP
        for (Trial t : trials) {
            pipSum += t.getPipProgression() != null ? t.getPipProgression() : 0;

            int killsInTrial = 0;
            boolean gateEscape = false;
            boolean hatchEscape = false;
            int gates = 0, hatches = 0;

            for (TrialSurvivor s : t.getSurvivors()) {
                String outcome = s.getOutcome().name();
                if (outcome.equals("KILLED") || outcome.equals("SACRIFICED") || outcome.equals("DISCONNECTED")) {
                    killsInTrial++;
                } else if (outcome.equals("ESCAPED")) {
                    gateEscape = true; gates++;
                } else if (outcome.equals("HATCH_ESCAPE")) {
                    hatchEscape = true; hatches++;
                }
            }

            totalKills += killsInTrial;
            if (killsInTrial == 4) fourKCount++;
            if (killsInTrial <= 1) lossCount++;
            if (hatchEscape) hatchCount++;
            if ((killsInTrial == 2 || killsInTrial == 3) && gateEscape) twoToThreeKillsWithGates++;

            // --- FINANCIAL MATH ---
            int netIncome = t.getNetIncome() != null ? t.getNetIncome() : 0;
            if (netIncome > 0) {
                totalRevenue += netIncome;
                if (netIncome > maxWinAmount) { maxWinAmount = netIncome; maxWinTrialNum = t.getTrialNumber(); }
            } else if (netIncome < 0) {
                totalDebt += Math.abs(netIncome);
                if (netIncome < maxLossAmount) { maxLossAmount = netIncome; maxLossTrialNum = t.getTrialNumber(); }
            }

            // --- IRON MAN MATH ---
            if (Boolean.TRUE.equals(t.getBurnedMulligan())) mulligansBurned++;
            if (Boolean.TRUE.equals(t.getFlawlessTrial())) flawlessCount++;

            // --- PERKS & EMBLEMS ---
            for (Perk p : t.getPerks()) {
                perkCounts.put(p.getName(), perkCounts.getOrDefault(p.getName(), 0) + 1);
                totalPerkCost += p.getCost() != null ? p.getCost() : 0;
                totalPerksEquipped++;
            }

            t.getEmblems().stream().filter(e -> e.getType().name().equals("IRIDESCENT"))
                    .forEach(e -> iriEmblemCounts.put(e.getCategory().name(), iriEmblemCounts.get(e.getCategory().name()) + 1));

            // --- ROSTER AGGREGATION ---
            String kName = t.getKiller().getName();
            killerStats.putIfAbsent(kName, new KillerAgg());
            KillerAgg agg = killerStats.get(kName);

            agg.matches++;
            agg.kills += killsInTrial;
            agg.pips += (t.getPipProgression() != null ? t.getPipProgression() : 0);
            if (killsInTrial == 4) agg.fourKs++;
            if (killsInTrial <= 1) agg.losses++;
            agg.gateEscapes += gates;
            agg.hatchEscapes += hatches;
        }

        // Helper to format percentages
        var formatPct = (java.util.function.BiFunction<Integer, Integer, Double>) (count, total) ->
                Math.round(((double) count / total) * 1000.0) / 10.0;

        List<VariantStatsResponse.PerkStat> topPerks = perkCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(4)
                .map(e -> new VariantStatsResponse.PerkStat(e.getKey(), formatPct.apply(e.getValue(), matches)))
                .toList();

        List<VariantStatsResponse.EmblemStat> emblems = iriEmblemCounts.entrySet().stream()
                .map(e -> new VariantStatsResponse.EmblemStat(e.getKey(), formatPct.apply(e.getValue(), matches))).toList();

        // 4. --- AWARDS CALCULATION ---
        List<VariantStatsResponse.RosterAward> awards = new ArrayList<>();
        var sortedKillers = killerStats.entrySet().stream()
                .sorted((a, b) -> {
                    if (b.getValue().pips != a.getValue().pips) return b.getValue().pips - a.getValue().pips;
                    return Double.compare((double)b.getValue().kills/b.getValue().matches, (double)a.getValue().kills/a.getValue().matches);
                }).toList();

        if (!sortedKillers.isEmpty()) {
            var mvp = sortedKillers.get(0);
            awards.add(new VariantStatsResponse.RosterAward("MOST VALUABLE", mvp.getKey(),
                    (mvp.getValue().pips > 0 ? "+" : "") + mvp.getValue().pips + " Pips", "positive"));

            var executioner = killerStats.entrySet().stream().filter(e -> e.getValue().fourKs > 0)
                    .max(java.util.Comparator.comparingInt(e -> e.getValue().fourKs));
            executioner.ifPresent(e -> awards.add(new VariantStatsResponse.RosterAward("THE EXECUTIONER", e.getKey(), e.getValue().fourKs + " Total 4Ks", "positive")));

            var merciful = killerStats.entrySet().stream().filter(e -> e.getValue().hatchEscapes > 0)
                    .max(java.util.Comparator.comparingInt(e -> e.getValue().hatchEscapes));
            merciful.ifPresent(e -> awards.add(new VariantStatsResponse.RosterAward("THE MERCIFUL", e.getKey(), e.getValue().hatchEscapes + " Hatch Escapes", "positive")));

            var choker = killerStats.entrySet().stream().filter(e -> e.getValue().gateEscapes > 0)
                    .max(java.util.Comparator.comparingInt(e -> e.getValue().gateEscapes));
            choker.ifPresent(e -> awards.add(new VariantStatsResponse.RosterAward("ENDGAME CHOKER", e.getKey(), e.getValue().gateEscapes + " Gate Escapes", "negative")));

            if (sortedKillers.size() > 1) {
                var lvp = sortedKillers.get(sortedKillers.size()-1);
                if (!lvp.getKey().equals(mvp.getKey()) && lvp.getValue().losses > 0) {
                    awards.add(new VariantStatsResponse.RosterAward("WEAKEST LINK", lvp.getKey(),
                            (lvp.getValue().pips > 0 ? "+" : "") + lvp.getValue().pips + " Pips", "negative"));
                }
            }
        }

        // 5. --- ADEPT: TOP KILLERS ---
        List<VariantStatsResponse.KillerStat> topKillers = killerStats.entrySet().stream()
                .sorted((a, b) -> b.getValue().matches - a.getValue().matches).limit(4)
                .map(e -> new VariantStatsResponse.KillerStat(e.getKey(), formatPct.apply(e.getValue().matches, matches), formatPct.apply(e.getValue().kills, e.getValue().matches * 4)))
                .toList();

        // 6. --- FINANCIAL EXTREMES ---
        VariantStatsResponse.TrialRecord biggestWin = new VariantStatsResponse.TrialRecord(maxWinAmount, maxWinTrialNum);
        VariantStatsResponse.TrialRecord biggestLoss = new VariantStatsResponse.TrialRecord(maxLossAmount, maxLossTrialNum);
        VariantStatsResponse.FinancialExtremes financials = new VariantStatsResponse.FinancialExtremes(biggestWin, biggestLoss);

        // 7. --- TIME CALCULATIONS ---
        long totalMinutes = 0;
        int completedSeasons = 0;

        for (Season s : seasons) {
            if (s.getEndDate() != null && s.getStartDate() != null) {
                totalMinutes += java.time.Duration.between(s.getStartDate(), s.getEndDate()).toMinutes();
                completedSeasons++;
            }
        }

        String avgTime = null;
        if (completedSeasons > 0) {
            long avg = totalMinutes / completedSeasons;
            avgTime = String.format("%02d:%02d", avg / 60, avg % 60);
        }

        Double avgPerkVal = totalPerksEquipped > 0 ? Math.round(((double)totalPerkCost / totalPerksEquipped) * 10.0) / 10.0 : 0.0;

        // 8. --- THE MASTER RETURN ---
        return new VariantStatsResponse(
                matches, formatPct.apply(totalKills, matches * 4), formatPct.apply(fourKCount, matches),
                pipSum, formatPct.apply(lossCount, matches), formatPct.apply(hatchCount, matches), twoToThreeKillsWithGates,
                topPerks, emblems,
                awards, topKillers, financials, totalRevenue, totalDebt,
                avgTime, mulligansBurned, flawlessCount, avgPerkVal
        );
    }

    @Transactional
    public Season updateDraftState(UUID playerId, UUID seasonId, com.ledger.ledger_api.dto.DraftUpdateRequest request) {
        Season season = seasonRepo.findById(seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("Season not found"));

        if (!season.getPlayer().getId().equals(playerId)) {
            throw new IllegalStateException("You do not have permission to modify this season.");
        }

        // ANTI-CHEAT: Reject draft changes if they are locked into a trial!
        if (season.getCurrentPhase() != Season.SeasonPhase.DRAFTING) {
            throw new IllegalStateException("Cannot modify loadout while a trial is in progress.");
        }

        Map<String, Object> draft = season.getDraftState();
        if (draft == null) draft = new HashMap<>();

        // 1. Remember which killer they are currently looking at
        draft.put("currentKillerId", request.killerId());

        // 2. Safely extract the nested 'loadouts' dictionary (or create it)
        @SuppressWarnings("unchecked")
        Map<String, Map<String, List<Long>>> oldLoadouts =
                (Map<String, Map<String, List<Long>>>) draft.getOrDefault("loadouts", new HashMap<>());

        Map<String, Map<String, List<Long>>> loadouts = new java.util.HashMap<>(oldLoadouts);

        // 3. Package this specific killer's perks and addons
        Map<String, List<Long>> killerLoadout = new HashMap<>();
        killerLoadout.put("perks", request.perkIds() != null ? request.perkIds() : new ArrayList<>());
        killerLoadout.put("addons", request.addOnIds() != null ? request.addOnIds() : new ArrayList<>());

        // 4. Save it into the dictionary using the Killer ID as the key
        loadouts.put(String.valueOf(request.killerId()), killerLoadout);
        draft.put("loadouts", loadouts);

        season.setDraftState(new java.util.HashMap<>(draft));
        return seasonRepo.save(season);
    }

    // Sell Killer logic for Blood Money & Afterburn
    @Transactional
    public Season sellKiller(UUID playerId, UUID seasonId, Long killerId) {
        Season season = seasonRepo.findById(seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("Season not found"));

        if (!season.getPlayer().getId().equals(playerId)) {
            throw new IllegalStateException("You do not have permission to modify this season.");
        }

        if (season.getVariantType() != Season.VariantType.BLOOD_MONEY && season.getVariantType() != Season.VariantType.AFTERBURN) {
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
            state.put("cooldowns", new java.util.HashMap<String, Integer>());
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
