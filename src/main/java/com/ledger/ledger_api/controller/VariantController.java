package com.ledger.ledger_api.controller;

import com.ledger.ledger_api.entity.Player;
import com.ledger.ledger_api.entity.Season; // Or your SeasonDTO
import com.ledger.ledger_api.service.PlayerService;
import com.ledger.ledger_api.service.SeasonService; // Or VariantService
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/variants")
public class VariantController {

    private final PlayerService playerService;
    private final SeasonService seasonService; // Replace with your actual service

    public VariantController(PlayerService playerService, SeasonService seasonService) {
        this.playerService = playerService;
        this.seasonService = seasonService;
    }

    // Catches: GET /api/v1/variants/STANDARD/seasons
    @GetMapping("/{variantType}/seasons")
    public ResponseEntity<List<Season>> getSeasonsByVariant(
            @PathVariable String variantType,
            @AuthenticationPrincipal OAuth2User principal) {

        Player player = playerService.getOrCreatePlayer(principal.getName(), principal.getAttribute("email"), principal.getAttribute("name"));
        List<Season> pastSeasons = seasonService.getSeasonsByVariant(player.getId(), variantType);

        return ResponseEntity.ok(pastSeasons);
    }

    // Catches: GET /api/v1/variants/STANDARD/stats
    @GetMapping("/{variantType}/stats")
    public ResponseEntity<Map<String, Object>> getVariantStats(
            @PathVariable String variantType,
            @AuthenticationPrincipal OAuth2User principal) {

        Player player = playerService.getOrCreatePlayer(principal.getName(), principal.getAttribute("email"), principal.getAttribute("name"));
        Map<String, Object> stats = seasonService.getVariantStats(player.getId(), variantType);

        return ResponseEntity.ok(stats);
    }
}
