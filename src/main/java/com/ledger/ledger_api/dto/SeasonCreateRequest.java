package com.ledger.ledger_api.dto;

// CONTAINS: Variant type and initial settings

import com.ledger.ledger_api.entity.GradeRule;
import com.ledger.ledger_api.entity.Season;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SeasonCreateRequest(
        @NotNull(message = "Variant type is required")
        Season.VariantType variantType,

        @NotNull(message = "Starting grade is required")
        GradeRule.Grade startingGrade,

        // For the Afterburn variant, they must pass the ID of their completed Blood Money season
        UUID inheritedSeasonId,

        // This catches all the unique variant toggles (e.g., "consecutiveMatches": true, "startingFunds": 20)
        Map<String, Object> variantSettings,

        List<Long> unlockedKillerIds
) {}
