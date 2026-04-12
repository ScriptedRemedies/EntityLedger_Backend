package com.ledger.ledger_api.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.ledger_api.entity.AddOn;
import com.ledger.ledger_api.entity.Killer;
import com.ledger.ledger_api.entity.Perk;
import com.ledger.ledger_api.repository.AddOnRepository;
import com.ledger.ledger_api.repository.KillerRepository;
import com.ledger.ledger_api.repository.PerkRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final KillerRepository killerRepo;
    private final PerkRepository perkRepo;
    private final AddOnRepository addOnRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataSeeder(KillerRepository killerRepo, PerkRepository perkRepo, AddOnRepository addOnRepo) {
        this.killerRepo = killerRepo;
        this.perkRepo = perkRepo;
        this.addOnRepo = addOnRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedKillers();
        seedPerks();
        seedAddOns();
    }

    private void seedKillers() {
        ClassPathResource resource = new ClassPathResource("killers.json");
        if (!resource.exists()) return;

        try (InputStream inputStream = resource.getInputStream()) {
            List<KillerDTO> killers = objectMapper.readValue(inputStream, new TypeReference<>() {});

            for (KillerDTO dto : killers) {
                Killer killer = killerRepo.findByName(dto.name()).orElse(new Killer());
                killer.setName(dto.name());
                killer.setCost(dto.cost());
                killerRepo.save(killer);
            }
            System.out.println("Killers synchronized successfully from JSON.");
        } catch (Exception e) {
            System.err.println("Failed to seed killers: " + e.getMessage());
        }
    }

    private void seedPerks() {
        ClassPathResource resource = new ClassPathResource("perks.json");
        if (!resource.exists()) return;

        try (InputStream inputStream = resource.getInputStream()) {
            List<PerkDTO> perks = objectMapper.readValue(inputStream, new TypeReference<>() {});

            for (PerkDTO dto : perks) {
                Perk perk = perkRepo.findByName(dto.name()).orElse(new Perk());
                perk.setName(dto.name());
                perk.setCost(dto.cost());

                if (dto.killerName() != null) {
                    killerRepo.findByName(dto.killerName()).ifPresent(perk::setKiller);
                } else {
                    perk.setKiller(null);
                }

                perkRepo.save(perk);
            }
            System.out.println("Perks synchronized successfully from JSON.");
        } catch (Exception e) {
            System.err.println("Failed to seed perks: " + e.getMessage());
        }
    }

    private void seedAddOns() {
        ClassPathResource resource = new ClassPathResource("addons.json");
        if (!resource.exists()) return;

        try (InputStream inputStream = resource.getInputStream()) {
            List<AddOnDTO> addOns = objectMapper.readValue(inputStream, new TypeReference<>() {});

            for (AddOnDTO dto : addOns) {
                AddOn addOn = addOnRepo.findByName(dto.name()).orElse(new AddOn());
                addOn.setName(dto.name());
                // Note: If your AddOn entity does not have a setCost method yet, you may need to add it
                // to the AddOn.java file to support the Blood Money economy!
                addOn.setCost(dto.cost());

                if (dto.killerName() != null) {
                    killerRepo.findByName(dto.killerName()).ifPresent(addOn::setKiller);
                }

                addOnRepo.save(addOn);
            }
            System.out.println("Add-ons synchronized successfully from JSON.");
        } catch (Exception e) {
            System.err.println("Failed to seed add-ons: " + e.getMessage());
        }
    }

    // Internal DTOs that act as a strict blueprint for our JSON files
    private record KillerDTO(String name, Integer cost) {}
    private record PerkDTO(String name, Integer cost, String killerName) {}
    private record AddOnDTO(String name, Integer cost, String killerName) {}
}
