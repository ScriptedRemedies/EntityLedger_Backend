package com.ledger.ledger_api.controller;

// /api/v1/trials

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.dto.TrialSummaryResponse;
import com.ledger.ledger_api.entity.Player;
import com.ledger.ledger_api.service.PlayerService;
import com.ledger.ledger_api.service.TrialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trials")
public class TrialController {

    private final TrialService trialService;
    private final PlayerService playerService;

    public TrialController(TrialService trialService, PlayerService playerService) {
        this.trialService = trialService;
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<TrialSummaryResponse> logTrial(
            @Valid @RequestBody TrialSubmitRequest request,
            @AuthenticationPrincipal OAuth2User principal) {
        Player player = playerService.getOrCreatePlayer(principal.getName(), principal.getAttribute("email"), principal.getAttribute("name"));
        TrialSummaryResponse response = trialService.submitTrial(request, player.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(com.ledger.ledger_api.exception.ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(com.ledger.ledger_api.exception.ResourceNotFoundException ex) {
        // 1. Force the exact error message to print in the Render backend logs
        System.err.println("🚨 SILENT 404 REVEALED: " + ex.getMessage());

        // 2. Send the exact message back to the browser Network tab
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
