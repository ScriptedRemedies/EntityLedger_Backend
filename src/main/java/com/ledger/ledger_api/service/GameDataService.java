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
        // Returns both the universal perks and the specific adept perks for this killer
        List<Perk> availablePerks = new ArrayList<>();
        availablePerks.addAll(perkRepo.findByKillerIsNull());

        if (killerId != null) {
            availablePerks.addAll(perkRepo.findByKillerId(killerId));
        }
        return availablePerks;
    }

    public List<AddOn> getAddOnsForKiller(Long killerId) {
        return addOnRepo.findByKillerId(killerId);
    }
}
