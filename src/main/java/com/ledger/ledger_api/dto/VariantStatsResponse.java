package com.ledger.ledger_api.dto;

import java.util.List;

public record VariantStatsResponse(
        // Global Stats
        Integer matchesPlayed,
        Double killRate,
        Double fourKRate,
        Integer pipProgression,
        Double lossRate,
        Double hatchEscapeRate,
        Integer twoToThreeKillsWithGates,

        // Used in Standard, Blood Money, Afterburn
        List<PerkStat> topPerks,
        List<EmblemStat> iridescentEmblems,

        // --- NEW DYNAMIC STATS ---

        // Used in ALL variants
        List<RosterAward> rosterAwards,

        // Specific to Adept
        List<KillerStat> topKillers,

        // Specific to Blood Money & Afterburn
        FinancialExtremes financialExtremes,
        Integer totalRevenue,
        Integer totalDebt,

        // Specific to Iron Man
        String averageCompletionTime,
        Integer totalMulligansBurned,
        Integer flawlessTrials,

        // Specific to Chaos Shuffle
        Double averagePerkValue
) {
    // Nested Records
    public record PerkStat(String name, Double pickRate) {}
    public record EmblemStat(String category, Double rate) {}

    // Maps perfectly to your UI's award cards
    public record RosterAward(String name, String killerName, String detailText, String effect) {}

    public record KillerStat(String name, Double pickRate, Double killRate) {}

    public record FinancialExtremes(TrialRecord biggestWin, TrialRecord biggestLoss) {}
    public record TrialRecord(Integer amount, Integer trialNumber) {}
}
