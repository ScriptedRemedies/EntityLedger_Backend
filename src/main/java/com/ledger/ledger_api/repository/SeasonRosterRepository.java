package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.SeasonRoster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeasonRosterRepository extends JpaRepository<SeasonRoster, UUID> {

    // Finds a specific killer in a season (used when validating a trial)
    Optional<SeasonRoster> findBySeasonIdAndKillerId(UUID seasonId, Long killerId);

    // Finds the entire 36+ killer roster for a season (used for the dashboard UI)
    List<SeasonRoster> findBySeasonId(UUID seasonId);
}
