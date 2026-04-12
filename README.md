# EntityLedger_Backend

## 🗄️ Architecture & Maintenance Guide

This backend is built for long-term maintainability. Game **Data** (Killers, Perks) is configuration-driven via JSON, while Game **Logic** (Variants) is modular via the Strategy Pattern.

### 1. Updating Game Data (New Chapters/Characters)
You **do not** need to write Java code or SQL to add new characters or perks.

1. Navigate to `src/main/resources/`.
2. Open `killers.json` and/or `perks.json`.
3. Add the new entity to the JSON array following the existing schema.
    * *Example Killer:* `{"name": "The Unknown", "cost": 5}`
    * *Example Perk:* `{"name": "Unbound", "cost": 2, "killerName": "The Unknown"}`
4. Restart the Spring Boot application.
5. The `DataSeeder` will automatically parse the JSON, check for missing records, and safely insert the new data into the PostgreSQL database.

### 2. Updating Game Logic (Adding a New Variant)
When the community creates a new challenge variant, the core `TrialService` remains untouched.

**Step 1: Update the Entity**
Open `Season.java` and add the new variant name to the `VariantType` Enum.
* *Example:* `STANDARD, ADEPT, BLOOD_MONEY, RANDOMIZER`

**Step 2: Create the Strategy**
1. Navigate to the `com.ledger.strategy` package.
2. Create a new class (e.g., `RandomizerVariantStrategy.java`) that `implements VariantStrategy`.
3. Annotate the class with `@Component("RANDOMIZER")` — *The string must match the Enum exactly.*
4. Implement the required `validateTrialStart` and `applyTrialResults` methods with the custom rules for the new mode. Spring Boot will automatically register and route traffic to this file when a user selects that variant.
