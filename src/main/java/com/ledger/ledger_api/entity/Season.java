package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "seasons")
@Getter
@Setter
@NoArgsConstructor
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "inherited_season_id")
    private UUID inheritedSeasonId; // For Afterburn to reference the Blood Money season

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VariantType variantType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeasonStatus status;

    @Column(nullable = false)
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    private GradeRule.Grade currentGrade;

    private Integer currentPips = 0;

    // Maps directly to a PostgreSQL JSONB column for flexible variant rules
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> variantState;

    public enum VariantType {
        STANDARD, ADEPT, BLOOD_MONEY, AFTERBURN, CHAOS_SHUFFLE, IRON_MAN
    }

    public enum SeasonStatus {
        ACTIVE, COMPLETED, FAILED_TIME, FAILED_ROSTER
    }
}
