package com.ledger.ledger_api.dto;

import java.util.List;
import java.util.UUID;

public record TrialSummaryResponse(
        UUID trialId,
        Integer trialNumber,
        String killerName,
        Integer pipProgression,

        String currentGrade,
        Integer currentPips,

        // Match these exactly to the React property names
        List<String> survivorResults,
        List<EmblemDTO> emblems,
        List<PerkDTO> perks,
        List<AddonDTO> addons
) {
    public record EmblemDTO(String name, String iconUrl) {}
    public record PerkDTO(String name, String iconUrl) {}
    public record AddonDTO(String name, String iconUrl) {}
}
