package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.SeasonStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SeasonStatsRepository extends JpaRepository<SeasonStats, UUID> {
    // Standard CRUD operations are enough here since the ID is the SeasonId
}
