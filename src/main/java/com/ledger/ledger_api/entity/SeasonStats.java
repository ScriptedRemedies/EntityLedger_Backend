package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "season_stats")
@Getter
@Setter
@NoArgsConstructor
public class SeasonStats {

    @Id
    private UUID seasonId; // Shares the exact same UUID as the Season

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // Tells Hibernate to use the Season's ID as this entity's PK
    @JoinColumn(name = "season_id")
    private Season season;

    private Integer matchesPlayed = 0;
    private Double killRate = 0.0;
    private Double fourKRate = 0.0;
    private Double rosterSurvivalRate = 100.0;
    private Double hatchEscapeRate = 0.0;
    private Double gateEscapeRate = 0.0;
    private Integer totalIridescentEmblems = 0;

    // Stores just the IDs of the top perks/killers in JSON arrays for fast UI loading
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Long> topFourPerks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Long> topFourKillers;
}
