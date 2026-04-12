package com.ledger.ledger_api.controller;

// /api/v1/seasons

import com.ledger.ledger_api.dto.SeasonCreateRequest;
import com.ledger.ledger_api.dto.SeasonDetailsResponse;
import com.ledger.ledger_api.entity.Player;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.service.PlayerService;
import com.ledger.ledger_api.service.SeasonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seasons")
public class SeasonController {

    private final SeasonService seasonService;
    private final PlayerService playerService;

    public SeasonController(SeasonService seasonService, PlayerService playerService) {
        this.seasonService = seasonService;
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<Season> startSeason(@Valid @RequestBody SeasonCreateRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        Player player = playerService.getOrCreatePlayer(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
        Season newSeason = seasonService.startNewSeason(player.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newSeason);
    }

    @GetMapping("/active")
    public ResponseEntity<SeasonDetailsResponse> getActiveSeason(@AuthenticationPrincipal Jwt jwt) {
        Player player = playerService.getOrCreatePlayer(jwt.getSubject(), jwt.getClaimAsString("email"), jwt.getClaimAsString("name"));
        return ResponseEntity.ok(seasonService.getActiveSeasonDetails(player.getId()));
    }
}
