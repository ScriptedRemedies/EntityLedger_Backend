```mermaid
erDiagram
%% Core Entities
PLAYER {
UUID id PK
String username
String email
}

    SEASON {
        UUID id PK
        UUID player_id FK
        UUID inherited_season_id FK "Nullable (Afterburn)"
        String variant_type "Enum"
        String status "Enum"
        DateTime start_date
        DateTime end_date
        String current_grade "Enum"
        Integer current_pips
        JSONB variant_state "Handles tokens, $, restrictions"
    }

    SEASON_ROSTER {
        UUID id PK
        UUID season_id FK
        Long killer_id FK
        String status "Enum (AVAILABLE, DEAD, etc.)"
    }

    SEASON_STATS {
        UUID season_id PK "Also FK to Season"
        Integer matches_played
        Double kill_rate
        Double four_k_rate
        Double roster_survival_rate
        Double hatch_escape_rate
        Double gate_escape_rate
        Integer total_iridescent_emblems
        JSONB top_four_perks "Array of Perk IDs"
        JSONB top_four_killers "Array of Killer IDs"
    }

    TRIAL {
        UUID id PK
        UUID season_id FK
        Long killer_id FK
        Integer trial_number
        Integer pip_progression
    }

    TRIAL_SURVIVOR {
        UUID id PK
        UUID trial_id FK
        String outcome "Enum"
    }

    %% Reference Data
    KILLER {
        Long id PK
        String name
        Integer cost
    }

    PERK {
        Long id PK
        String name
        Integer cost
        Long killer_id FK "Nullable (Adept mapping)"
    }

    ADD_ON {
        Long id PK
        String name
        Long killer_id FK
    }

    EMBLEM {
        Long id PK
        String category "Enum"
        String type "Enum"
    }

    GRADE_RULE {
        Long id PK
        String grade "Enum"
        Integer pip_threshold
    }

    %% Many-to-Many Join Tables (Managed by Spring/Hibernate)
    TRIAL_PERK {
        UUID trial_id FK
        Long perk_id FK
    }

    TRIAL_ADD_ON {
        UUID trial_id FK
        Long add_on_id FK
    }

    TRIAL_EMBLEM {
        UUID trial_id FK
        Long emblem_id FK
    }

    %% Relationships
    PLAYER ||--o{ SEASON : "plays"
    SEASON |o--o| SEASON : "inherits from (Afterburn)"
    SEASON ||--o{ SEASON_ROSTER : "tracks killer states"
    SEASON ||--|| SEASON_STATS : "calculates"
    SEASON ||--o{ TRIAL : "contains"
    
    KILLER ||--o{ SEASON_ROSTER : "is tracked in"
    KILLER ||--o{ TRIAL : "is played in"
    KILLER ||--o{ PERK : "owns (Adept)"
    KILLER ||--o{ ADD_ON : "owns"

    TRIAL ||--|{ TRIAL_SURVIVOR : "faces 4"
    
    %% Join Table Connections
    TRIAL ||--o{ TRIAL_PERK : "uses"
    PERK ||--o{ TRIAL_PERK : "equipped in"
    
    TRIAL ||--o{ TRIAL_ADD_ON : "uses"
    ADD_ON ||--o{ TRIAL_ADD_ON : "equipped in"
    
    TRIAL ||--o{ TRIAL_EMBLEM : "earns"
    EMBLEM ||--o{ TRIAL_EMBLEM : "awarded in"
