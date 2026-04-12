package com.ledger.ledger_api.job;

// Runs the cron job that automatically fails any active seasons on the 13th of the month

import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.repository.SeasonRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SeasonExpirationJob {

    private final SeasonRepository seasonRepo;

    public SeasonExpirationJob(SeasonRepository seasonRepo) {
        this.seasonRepo = seasonRepo;
    }

    // Cron syntax: Seconds Minutes Hours DayOfMonth Month DayOfWeek
    // "0 0 0 13 * ?" = Run at midnight (00:00:00) on the 13th of every month
    @Scheduled(cron = "0 0 0 13 * ?")
    @Transactional
    public void expireActiveSeasons() {
        List<Season> activeSeasons = seasonRepo.findAllByStatus(Season.SeasonStatus.ACTIVE);

        for (Season season : activeSeasons) {
            season.setStatus(Season.SeasonStatus.FAILED_TIME);
        }

        seasonRepo.saveAll(activeSeasons);
    }
}
