package com.ledger.ledger_api.service;

// PURPOSE: Fetches the static Killer/Perk data for the UI

import com.ledger.ledger_api.entity.AddOn;
import com.ledger.ledger_api.entity.Killer;
import com.ledger.ledger_api.entity.Perk;
import com.ledger.ledger_api.repository.AddOnRepository;
import com.ledger.ledger_api.repository.KillerRepository;
import com.ledger.ledger_api.repository.PerkRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GameDataService {

    private final KillerRepository killerRepo;
    private final PerkRepository perkRepo;
    private final AddOnRepository addOnRepo;

    public GameDataService(KillerRepository killerRepo, PerkRepository perkRepo, AddOnRepository addOnRepo) {
        this.killerRepo = killerRepo;
        this.perkRepo = perkRepo;
        this.addOnRepo = addOnRepo;
    }

    public List<Killer> getAllKillers() {
        return killerRepo.findAll();
    }

    public List<Perk> getAvailablePerksForKiller(Long killerId) {
        // 1. If no specific killer is requested, return the entire catalog of 138+ perks
        if (killerId == null) {
            return perkRepo.findAll();
        }

        // 2. If a killer IS specified, return only Universal perks + Their specific Adept perks
        List<Perk> availablePerks = new ArrayList<>();
        availablePerks.addAll(perkRepo.findByKillerIsNull());
        availablePerks.addAll(perkRepo.findByKillerId(killerId));

        return availablePerks;
    }

    public List<AddOn> getAddOnsForKiller(Long killerId) {
        return addOnRepo.findByKillerId(killerId);
    }
}
