package com.ledger.ledger_api.event;

// PURPOSE: Listens for the Trial Completed Event and recalculates the season stats in the background

import com.ledger.ledger_api.entity.SeasonStats;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.entity.TrialSurvivor;
import com.ledger.ledger_api.repository.SeasonStatsRepository;
import com.ledger.ledger_api.repository.TrialRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Component
public class StatAggregationListener {

    private final SeasonStatsRepository statsRepo;
    private final TrialRepository trialRepo;

    public StatAggregationListener(SeasonStatsRepository statsRepo, TrialRepository trialRepo) {
        this.statsRepo = statsRepo;
        this.trialRepo = trialRepo;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTrialCompleted(TrialCompletedEvent event) {
        UUID seasonId = event.seasonId();

        List<Trial> trials = trialRepo.findAllBySeasonIdOrderByTrialNumberAsc(seasonId);
        if (trials.isEmpty()) return;

        SeasonStats stats = statsRepo.findById(seasonId).orElseThrow();

        int matchesPlayed = trials.size();
        int totalKills = 0;
        int totalHatchEscapes = 0;
        int totalGateEscapes = 0;
        int fourKGames = 0;

        for (Trial trial : trials) {
            // Wait to implement TrialSurvivor fetching based on your specific cascade settings,
            // but the logic structure remains exactly like this:
            int killsInMatch = 0;
            // Assuming we have a way to fetch the 4 survivors for this trial:
            // for (TrialSurvivor survivor : fetchSurvivorsForTrial(trial.getId())) {
            //     if (survivor.getOutcome() == KILLED || survivor.getOutcome() == SACRIFICED) killsInMatch++;
            //     else if (survivor.getOutcome() == HATCH_ESCAPE) totalHatchEscapes++;
            //     else if (survivor.getOutcome() == ESCAPED) totalGateEscapes++;
            // }

            totalKills += killsInMatch;
            if (killsInMatch == 4) fourKGames++;
        }

        stats.setMatchesPlayed(matchesPlayed);
        stats.setKillRate((totalKills / (double) (matchesPlayed * 4)) * 100);
        stats.setFourKRate((fourKGames / (double) matchesPlayed) * 100);
        stats.setHatchEscapeRate((totalHatchEscapes / (double) (matchesPlayed * 4)) * 100);
        stats.setGateEscapeRate((totalGateEscapes / (double) (matchesPlayed * 4)) * 100);

        statsRepo.save(stats);
    }
}
