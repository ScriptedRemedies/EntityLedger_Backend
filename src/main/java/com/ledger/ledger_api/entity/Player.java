package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // This stores the unique ID from Google/Discord so we can map their login
    @Column(nullable = false, unique = true)
    private String authProviderId;

    @Column(nullable = false)
    private String email;

    private String username;
}
