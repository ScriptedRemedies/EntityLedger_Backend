package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "emblems")
@Getter
@Setter
@NoArgsConstructor
public class Emblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmblemCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmblemType type;

    public enum EmblemCategory {
        NONE, BRONZE, SILVER, GOLD, IRIDESCENT
    }

    public enum EmblemType {
        DEVOUT, MALICIOUS, GATEKEEPER, CHASER
    }
}
