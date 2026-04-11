package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "trial_survivors")
@Getter
@Setter
@NoArgsConstructor
public class TrialSurvivor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_id", nullable = false)
    private Trial trial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SurvivorOutcome outcome;

    public enum SurvivorOutcome {
        ESCAPED, SACRIFICED, KILLED, DISCONNECTED, HATCH_ESCAPE
    }
}
