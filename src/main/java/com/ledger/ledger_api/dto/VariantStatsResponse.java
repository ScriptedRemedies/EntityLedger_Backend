package com.ledger.ledger_api.dto;

import java.util.List;

public record VariantStatsResponse(
        Integer matchesPlayed,
        Double killRate,
        Double fourKRate,
        Integer pipProgression,
        Double lossRate,
        Double hatchEscapeRate,
        Integer twoToThreeKillsWithGates,
        List<PerkStat> topPerks,
        List<EmblemStat> iridescentEmblems
) {
    public record PerkStat(String name, Double pickRate) {}
    public record EmblemStat(String category, Double rate) {}
}
