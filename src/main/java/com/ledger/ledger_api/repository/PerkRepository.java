package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.Perk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerkRepository extends JpaRepository<Perk, Long> {

    // CRITICAL: Used for the Adept variant and Afterburn to lock out specific perks
    List<Perk> findByKillerId(Long killerId);

    // Finds all universal perks (where killer is null)
    List<Perk> findByKillerIsNull();
}
