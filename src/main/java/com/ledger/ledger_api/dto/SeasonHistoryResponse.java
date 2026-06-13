package com.ledger.ledger_api.dto;

import com.ledger.ledger_api.entity.GradeRule;
import com.ledger.ledger_api.entity.Season;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SeasonHistoryResponse(
        UUID id,
        Season.VariantType variantType,
        Season.SeasonStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        GradeRule.Grade currentGrade,
        Integer currentPips,

        // Explicitly mapped to "roster" (singular) for React!
        List<SeasonDetailsResponse.RosterItemDto> roster,

        String characterName,
        String characterImageUrl
) {}
