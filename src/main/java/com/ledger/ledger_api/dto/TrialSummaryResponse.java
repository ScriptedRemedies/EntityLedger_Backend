package com.ledger.ledger_api.dto;

import java.util.List;
import java.util.UUID;

public record TrialSummaryResponse(
        UUID trialId,
        Integer trialNumber,
        String killerName,
        Integer killerCost,
        Integer pipProgression,

        String currentGrade,
        Integer currentPips,

        Integer netIncome,
        Boolean earnedMulligan,
        Boolean burnedMulligan,
        Boolean flawlessTrial,

        Integer runningBalance,
        Boolean usedReRollToken,
        Integer remainingTokens,

        // Match these exactly to the React property names
        List<String> survivorResults,
        List<EmblemDTO> emblems,
        List<PerkDTO> perks,
        List<AddonDTO> addons,
        String seasonStatus
) {
    public record EmblemDTO(String name, String iconUrl) {}
    public record PerkDTO(String name, String iconUrl, Integer cost) {}
    public record AddonDTO(String name, String iconUrl, Integer cost) {}
}
