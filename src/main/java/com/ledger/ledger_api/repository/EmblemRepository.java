package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.Emblem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmblemRepository extends JpaRepository<Emblem, Long> {

    // Helps map the UI input (e.g., "GOLD", "DEVOUT") to the database record
    Optional<Emblem> findByCategoryAndType(Emblem.EmblemCategory category, Emblem.EmblemType type);
}
