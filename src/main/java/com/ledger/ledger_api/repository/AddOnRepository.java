package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.AddOn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddOnRepository extends JpaRepository<AddOn, Long> {

    // To populate the UI dropdowns when a user selects a specific killer
    List<AddOn> findByKillerId(Long killerId);

    // Prevent duplicates in the DataSeeder by finding perks by name
    Optional<AddOn> findByName(String name);
}
