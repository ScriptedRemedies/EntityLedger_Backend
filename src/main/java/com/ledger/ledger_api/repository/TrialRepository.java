package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.Trial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrialRepository extends JpaRepository<Trial, UUID> {

    // Fetches the timeline of a season in order
    List<Trial> findAllBySeasonIdOrderByTrialNumberAsc(UUID seasonId);

    // Helps calculate the next trial number quickly
    int countBySeasonId(UUID seasonId);
}
