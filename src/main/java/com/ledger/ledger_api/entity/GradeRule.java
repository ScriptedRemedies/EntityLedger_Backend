package com.ledger.ledger_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "grade_rules")
@Getter
@Setter
@NoArgsConstructor
public class GradeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Grade grade;

    @Column(nullable = false)
    private Integer pipThreshold;

    public enum Grade {
        ASH_IV, ASH_III, ASH_II, ASH_I,
        BRONZE_IV, BRONZE_III, BRONZE_II, BRONZE_I,
        SILVER_IV, SILVER_III, SILVER_II, SILVER_I,
        GOLD_IV, GOLD_III, GOLD_II, GOLD_I,
        IRIDESCENT_IV, IRIDESCENT_III, IRIDESCENT_II, IRIDESCENT_I
    }
}
