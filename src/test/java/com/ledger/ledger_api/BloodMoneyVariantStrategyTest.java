package com.ledger.ledger_api;

import com.ledger.ledger_api.dto.TrialSubmitRequest;
import com.ledger.ledger_api.entity.*;
import com.ledger.ledger_api.repository.AddOnRepository;
import com.ledger.ledger_api.repository.PerkRepository;
import com.ledger.ledger_api.strategy.BloodMoneyVariantStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloodMoneyVariantStrategyTest {

    @Mock
    private PerkRepository perkRepo;

    @Mock
    private AddOnRepository addOnRepo;

    @InjectMocks
    private BloodMoneyVariantStrategy strategy;

    @Test
    void applyTrialResults_calculatesCorrectBalance() {
        // --- ARRANGE (Set up the scenario) ---

        // 1. Set starting balance to 100
        Season season = new Season();
        Map<String, Object> state = new HashMap<>();
        state.put("balance", 100);
        season.setVariantState(state);

        // 2. Set up a default, living killer (no specific status needed to pass)
        SeasonRoster roster = new SeasonRoster();

        // 3. Set up the trial (1 pip = +2 funds)
        Trial trial = new Trial();
        trial.setPipProgression(1);

        // 4. MOCK the request to avoid constructor errors completely
        TrialSubmitRequest request = mock(TrialSubmitRequest.class);

        // Tell the fake request exactly what to return when the strategy asks for it
        when(request.perkIds()).thenReturn(List.of(10L));
        when(request.addOnIds()).thenReturn(List.of(20L));
        when(request.survivorOutcomes()).thenReturn(List.of(
                TrialSurvivor.SurvivorOutcome.KILLED,
                TrialSurvivor.SurvivorOutcome.KILLED,
                TrialSurvivor.SurvivorOutcome.ESCAPED,
                TrialSurvivor.SurvivorOutcome.ESCAPED
        ));

        // 5. Mock the database item costs
        Perk mockPerk = new Perk();
        mockPerk.setCost(3); // Perk cost = -3
        when(perkRepo.findAllById(List.of(10L))).thenReturn(List.of(mockPerk));

        AddOn mockAddOn = new AddOn();
        mockAddOn.setCost(2); // Add-on cost = -2
        when(addOnRepo.findAllById(List.of(20L))).thenReturn(List.of(mockAddOn));

        // --- ACT (Run the math) ---
        strategy.applyTrialResults(season, roster, trial, request);

        // --- ASSERT (Verify the results) ---
        // Income: (1 pip * 2) + (2 kills * 2) = +6
        // Cost: (3 + 2) = -5
        // Net Change: +1.
        // Expected Final Balance: 100 + 1 = 101.

        assertEquals(101, season.getVariantState().get("balance"), "The Blood Money math did not calculate correctly!");
    }
}
