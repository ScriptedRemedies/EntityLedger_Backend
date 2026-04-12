package com.ledger.ledger_api.service;

// PURPOSE: Handles User fetching/creation via the OAuth token

import com.ledger.ledger_api.entity.Player;
import com.ledger.ledger_api.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final PlayerRepository playerRepo;

    public PlayerService(PlayerRepository playerRepo) {
        this.playerRepo = playerRepo;
    }

    @Transactional
    public Player getOrCreatePlayer(String authProviderId, String email, String username) {
        return playerRepo.findByAuthProviderId(authProviderId)
                .orElseGet(() -> {
                    Player newPlayer = new Player();
                    newPlayer.setAuthProviderId(authProviderId);
                    newPlayer.setEmail(email);
                    newPlayer.setUsername(username);
                    return playerRepo.save(newPlayer);
                });
    }
}
