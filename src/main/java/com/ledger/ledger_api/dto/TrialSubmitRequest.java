package com.ledger.ledger_api.dto;

import com.ledger.ledger_api.entity.TrialSurvivor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TrialSubmitRequest(
        @NotNull(message = "Killer ID is required")
        Long killerId,

        // NEW: Emblem data needed to populate the trial_emblems table
        List<EmblemPayload> emblems,

        // The calculated pip progression based on emblems (can be negative)
        @NotNull(message = "Pip progression is required")
        Integer pipProgression,

        // The user can equip up to 4 perks
        @Size(max = 4, message = "Cannot equip more than 4 perks")
        List<Long> perkIds,

        @Size(max = 2, message = "Cannot equip more than 2 add-ons")
        List<Long> addOnIds,

        // Exactly 4 survivor outcomes required
        @NotNull(message = "Survivor outcomes are required")
        @Size(min = 4, max = 4, message = "Exactly 4 survivor outcomes must be provided")
        List<TrialSurvivor.SurvivorOutcome> survivorOutcomes,

        // Blood Money variant fields (can be null for other variants)
        Integer kills,
        Integer gensLeft,
        Boolean closedHatch,
        Boolean genBeforeHook,
        Boolean lastGenCompleted,
        Boolean gateOpened,

        // Chaos Shuffle Re-Roll Tokens
        Boolean usedReRollToken
) {}

