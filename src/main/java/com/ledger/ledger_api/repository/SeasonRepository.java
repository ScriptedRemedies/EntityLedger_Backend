package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeasonRepository extends JpaRepository<Season, UUID> {

    // Crucial for ensuring a user only has ONE active season at a time
    Optional<Season> findByPlayerIdAndStatus(UUID playerId, Season.SeasonStatus status);

    // For fetching their entire history
    List<Season> findAllByPlayerIdAndVariantTypeOrderByStartDateDesc(UUID playerId, Season.VariantType variantType);

    // For the Scheduled Cron Job: Finds all active seasons to check if time ran out
    List<Season> findAllByStatus(Season.SeasonStatus status);
}
