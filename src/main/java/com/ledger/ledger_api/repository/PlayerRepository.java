package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {

    // Need this to look up a user when they log in via Google/Discord
    Optional<Player> findByAuthProviderId(String authProviderId);
}
