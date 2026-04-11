package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "trials")
@Getter
@Setter
@NoArgsConstructor
public class Trial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "killer_id", nullable = false)
    private Killer killer;

    @Column(nullable = false)
    private Integer trialNumber;

    private Integer pipProgression; // Can be negative, zero, or positive

    // --- Join Tables Handled by Hibernate ---

    @ManyToMany
    @JoinTable(
            name = "trial_perks",
            joinColumns = @JoinColumn(name = "trial_id"),
            inverseJoinColumns = @JoinColumn(name = "perk_id")
    )
    private Set<Perk> perks = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "trial_add_ons",
            joinColumns = @JoinColumn(name = "trial_id"),
            inverseJoinColumns = @JoinColumn(name = "add_on_id")
    )
    private Set<AddOn> addOns = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "trial_emblems",
            joinColumns = @JoinColumn(name = "trial_id"),
            inverseJoinColumns = @JoinColumn(name = "emblem_id")
    )
    private Set<Emblem> emblems = new HashSet<>();
}
