package com.ledger.ledger_api.controller;

// /api/v1/players

import com.ledger.ledger_api.entity.Player;
import com.ledger.ledger_api.service.PlayerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping("/sync")
    public ResponseEntity<Player> syncUser(@AuthenticationPrincipal Jwt jwt) {
        // Extracts data from the secure token to create/fetch the user profile
        Player player = playerService.getOrCreatePlayer(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name")
        );
        return ResponseEntity.ok(player);
    }
}
