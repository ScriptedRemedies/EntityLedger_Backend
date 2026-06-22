package com.ledger.ledger_api.dto;

import java.util.List;

public record DraftUpdateRequest (
        Long killerId,
        List<Long> perkIds,
        List<Long> addOnIds
) {}
