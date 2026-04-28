package com.ledger.ledger_api.controller;

// /api/v1/players

import com.ledger.ledger_api.entity.Player;
import com.ledger.ledger_api.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping("/sync")
    public ResponseEntity<Player> syncUser(@AuthenticationPrincipal OAuth2User principal) {

        // Extract from the OAuth2User attributes instead of claims
        Player player = playerService.getOrCreatePlayer(
                principal.getName(), // Subject ID
                principal.getAttribute("email"),
                principal.getAttribute("name")
        );
        return ResponseEntity.ok(player);
    }

    @GetMapping
    public ResponseEntity<List<Player>> getAllPlayers() {
        return ResponseEntity.ok(playerService.getAllPlayers());
    }
}
