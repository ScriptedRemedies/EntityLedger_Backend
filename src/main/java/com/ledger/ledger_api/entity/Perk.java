package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "perks")
@Getter
@Setter
@NoArgsConstructor
public class Perk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private Integer cost;

    // The foreign key linking back to the Killer.
    // Left nullable because universal perks do not belong to a specific killer.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "killer_id")
    private Killer killer;
}
