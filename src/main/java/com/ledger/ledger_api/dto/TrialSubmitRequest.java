package com.ledger.ledger_api.dto;

// CONTAINS: Killer, perks, addons, and survivor outcomes

import com.ledger.ledger_api.entity.TrialSurvivor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TrialSubmitRequest(
        @NotNull(message = "Killer ID is required")
        Long killerId,

        // The calculated pip progression based on emblems (can be negative)
        @NotNull(message = "Pip progression is required")
        Integer pipProgression,

        // The user can equip up to 4 perks, but could be 0 for Chaos Shuffle/Blood Money
        @Size(max = 4, message = "Cannot equip more than 4 perks")
        List<Long> perkIds,

        @Size(max = 2, message = "Cannot equip more than 2 add-ons")
        List<Long> addOnIds,

        // There must ALWAYS be exactly 4 survivor outcomes logged
        @NotNull(message = "Survivor outcomes are required")
        @Size(min = 4, max = 4, message = "Exactly 4 survivor outcomes must be provided")
        List<TrialSurvivor.SurvivorOutcome> survivorOutcomes
) {}
