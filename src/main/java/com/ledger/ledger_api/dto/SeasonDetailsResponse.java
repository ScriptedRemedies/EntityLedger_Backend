package com.ledger.ledger_api.dto;

// CONTAINS: Sending the Season, Stats, and Roster back to the UI

import com.ledger.ledger_api.entity.GradeRule;
import com.ledger.ledger_api.entity.Season;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SeasonDetailsResponse(
        UUID seasonId,
        Season.VariantType variantType,
        Season.SeasonStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        GradeRule.Grade currentGrade,
        Integer currentPips,

        // Variant state (e.g., {"balance": 22} or {"mulliganToken": 1})
        Map<String, Object> variantState,

        // Nested Stats Object
        StatsDto stats,

        // The list of all 36+ killers and their current status for this season
        List<RosterItemDto> roster
) {
    // Nested records to keep the payload structured
    public record StatsDto(
            Integer matchesPlayed,
            Double killRate,
            Double fourKRate,
            Double rosterSurvivalRate,
            Double hatchEscapeRate,
            Double gateEscapeRate,
            Integer totalIridescentEmblems,
            List<Long> topFourPerks,
            List<Long> topFourKillers
    ) {}

    public record RosterItemDto(
            Long killerId,
            String killerName,
            String status, // AVAILABLE, DEAD, SOLD, COOLDOWN
            Integer cost // Relevant for Blood Money
    ) {}
}
