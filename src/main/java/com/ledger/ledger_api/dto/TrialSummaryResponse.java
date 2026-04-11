package com.ledger.ledger_api.dto;

import java.util.List;
import java.util.UUID;

public record TrialSummaryResponse(
        UUID trialId,
        Integer trialNumber,
        String killerName,
        Integer pipProgression,
        List<String> perkNames,
        List<String> addOnNames,
        List<String> survivorOutcomes
) {}
