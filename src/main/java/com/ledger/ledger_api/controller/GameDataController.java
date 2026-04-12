package com.ledger.ledger_api.controller;

// /api/v1/reference-data

import com.ledger.ledger_api.entity.AddOn;
import com.ledger.ledger_api.entity.Killer;
import com.ledger.ledger_api.entity.Perk;
import com.ledger.ledger_api.service.GameDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reference-data")
public class GameDataController {

    private final GameDataService gameDataService;

    public GameDataController(GameDataService gameDataService) {
        this.gameDataService = gameDataService;
    }

    @GetMapping("/killers")
    public ResponseEntity<List<Killer>> getAllKillers() {
        return ResponseEntity.ok(gameDataService.getAllKillers());
    }

    @GetMapping("/perks")
    public ResponseEntity<List<Perk>> getPerks(@RequestParam(required = false) Long killerId) {
        return ResponseEntity.ok(gameDataService.getAvailablePerksForKiller(killerId));
    }

    @GetMapping("/addons")
    public ResponseEntity<List<AddOn>> getAddOns(@RequestParam Long killerId) {
        return ResponseEntity.ok(gameDataService.getAddOnsForKiller(killerId));
    }
}
