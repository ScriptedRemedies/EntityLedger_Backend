package com.ledger.ledger_api.repository;

import com.ledger.ledger_api.entity.Killer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KillerRepository extends JpaRepository<Killer, Long> {
    Optional<Killer> findByName(String name);
}
