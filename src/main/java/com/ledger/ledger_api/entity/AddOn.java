package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "add_ons")
@Getter
@Setter
@NoArgsConstructor
public class AddOn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Add-ons must belong to a killer, so this is not nullable.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "killer_id", nullable = false)
    private Killer killer;
}
