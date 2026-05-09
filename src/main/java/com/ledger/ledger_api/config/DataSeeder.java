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
            List<Killer> allExisting = killerRepo.findAll();

            for (KillerDTO dto : killers) {
                // SMART LOOKUP: Try Code first. If not found, try Name. If neither, create new.
                Killer killer = killerRepo.findByCode(dto.code())
                        .orElseGet(() -> killerRepo.findByName(dto.name())
                                .orElse(new Killer()));

                killer.setCode(dto.code());
                killer.setName(dto.name());
                killer.setCost(dto.cost());
                killer.setIsActive(true);

                killerRepo.save(killer);

                // Remove this killer from the "leftovers" list
                allExisting.removeIf(k -> k.getId() != null && k.getId().equals(killer.getId()));
            }

            // Deactivate any killers no longer in the JSON
            for (Killer leftover : allExisting) {
                leftover.setIsActive(false);
                killerRepo.save(leftover);
            }

            System.out.println("Killers synchronized safely from JSON.");
        } catch (Exception e) {
            System.err.println("Failed to seed killers: " + e.getMessage());
        }
    }

    private void seedPerks() {
        ClassPathResource resource = new ClassPathResource("perks.json");
        if (!resource.exists()) return;

        try (InputStream inputStream = resource.getInputStream()) {
            List<PerkDTO> perks = objectMapper.readValue(inputStream, new TypeReference<>() {});
            List<Perk> allExisting = perkRepo.findAll();

            for (PerkDTO dto : perks) {
                Perk perk = perkRepo.findByCode(dto.code())
                        .orElseGet(() -> perkRepo.findByName(dto.name())
                                .orElse(new Perk()));

                perk.setCode(dto.code());
                perk.setName(dto.name());
                perk.setCost(dto.cost());
                perk.setIsActive(true);

                if (dto.killerName() != null) {
                    killerRepo.findByName(dto.killerName()).ifPresent(perk::setKiller);
                } else {
                    perk.setKiller(null);
                }

                perkRepo.save(perk);
                allExisting.removeIf(p -> p.getId() != null && p.getId().equals(perk.getId()));
            }

            for (Perk leftover : allExisting) {
                leftover.setIsActive(false);
                perkRepo.save(leftover);
            }

            System.out.println("Perks synchronized safely from JSON.");
        } catch (Exception e) {
            System.err.println("Failed to seed perks: " + e.getMessage());
        }
    }

    private void seedAddOns() {
        ClassPathResource resource = new ClassPathResource("addons.json");
        if (!resource.exists()) return;

        try (InputStream inputStream = resource.getInputStream()) {
            List<AddOnDTO> addOns = objectMapper.readValue(inputStream, new TypeReference<>() {});
            List<AddOn> allExisting = addOnRepo.findAll();

            for (AddOnDTO dto : addOns) {
                AddOn addOn = addOnRepo.findByCode(dto.code())
                        .orElseGet(() -> addOnRepo.findByName(dto.name())
                                .orElse(new AddOn()));

                addOn.setCode(dto.code());
                addOn.setName(dto.name());
                addOn.setCost(dto.cost());
                addOn.setIsActive(true);

                if (dto.killerName() != null) {
                    killerRepo.findByName(dto.killerName()).ifPresent(addOn::setKiller);
                }

                addOnRepo.save(addOn);
                allExisting.removeIf(a -> a.getId() != null && a.getId().equals(addOn.getId()));
            }

            for (AddOn leftover : allExisting) {
                leftover.setIsActive(false);
                addOnRepo.save(leftover);
            }

            System.out.println("Add-ons synchronized safely from JSON.");
        } catch (Exception e) {
            System.err.println("Failed to seed add-ons: " + e.getMessage());
        }
    }

    private record KillerDTO(String code, String name, Integer cost) {}
    private record PerkDTO(String code, String name, Integer cost, String killerName) {}
    private record AddOnDTO(String code, String name, Integer cost, String killerName) {}
}
