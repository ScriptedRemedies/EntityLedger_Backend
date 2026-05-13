package com.ledger.ledger_api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "season_rosters")
@Getter
@Setter
@NoArgsConstructor
public class SeasonRoster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "killer_id", nullable = false)
    private Killer killer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RosterStatus status;

    public enum RosterStatus {
        AVAILABLE, DEAD, SOLD, COOLDOWN
    }
}
