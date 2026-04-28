package com.ledger.ledger_api.controller;

import com.ledger.ledger_api.dto.SeasonCreateRequest;
import com.ledger.ledger_api.dto.SeasonDetailsResponse;
import com.ledger.ledger_api.entity.Player;
import com.ledger.ledger_api.entity.Season;
import com.ledger.ledger_api.entity.Trial;
import com.ledger.ledger_api.service.PlayerService;
import com.ledger.ledger_api.service.SeasonService;
import com.ledger.ledger_api.service.TrialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seasons")
public class SeasonController {

    private final SeasonService seasonService;
    private final PlayerService playerService;
    private final TrialService trialService;

    public SeasonController(SeasonService seasonService, PlayerService playerService, TrialService trialService) {
        this.seasonService = seasonService;
        this.playerService = playerService;
        this.trialService = trialService;
    }

    @PostMapping
    public ResponseEntity<Season> startSeason(@Valid @RequestBody SeasonCreateRequest request, @AuthenticationPrincipal OAuth2User principal) {
        Player player = playerService.getOrCreatePlayer(principal.getName(), principal.getAttribute("email"), principal.getAttribute("name"));
        Season newSeason = seasonService.startNewSeason(player.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newSeason);
    }

    @GetMapping("/active")
    public ResponseEntity<SeasonDetailsResponse> getActiveSeason(@AuthenticationPrincipal OAuth2User principal) {
        Player player = playerService.getOrCreatePlayer(principal.getName(), principal.getAttribute("email"), principal.getAttribute("name"));
        return ResponseEntity.ok(seasonService.getActiveSeasonDetails(player.getId()));
    }

    @GetMapping("/{seasonId}/trials")
    public ResponseEntity<List<Trial>> getSeasonTrials(@PathVariable UUID seasonId, @AuthenticationPrincipal OAuth2User principal) {
        List<Trial> trialHistory = trialService.getTrialsBySeason(seasonId);
        return ResponseEntity.ok(trialHistory);
    }
}
