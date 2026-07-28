# FlameForge Feature Evaluation

This document contains the complete 100-candidate feature evaluation dataset and the rationale for the ten selected features in FlameForge v1.0.0.

## Selected Features

The following ten features were selected for implementation based on their scores and alignment with the core forge/reforge use case:

| ID     | Feature                               | Class     | Score | Decision    |
|--------|---------------------------------------|-----------|-------|-------------|
| F001   | Registered forge stations             | ELIGIBLE  | 96    | SELECTED    |
| F002   | Station profiles and visual themes    | ELIGIBLE  | 91    | SELECTED    |
| F003   | Catalyst items modifying weights      | ELIGIBLE  | 95    | SELECTED    |
| F004   | Break-protection wards                | ELIGIBLE  | 94    | SELECTED    |
| F005   | Per-tier pity system                  | ELIGIBLE  | 93    | SELECTED    |
| F006   | Reforge provenance/history           | ELIGIBLE  | 92    | SELECTED    |
| F007   | Per-tier cooldowns                    | ELIGIBLE  | 90    | SELECTED    |
| F008   | Exact preview/simulation              | ELIGIBLE  | 89    | SELECTED    |
| F009   | Jackpot announcements                 | ELIGIBLE  | 88    | SELECTED    |
| F010   | Audit transaction ledger              | ELIGIBLE  | 87    | SELECTED    |

## Selection Rationale

**F001 — Registered forge stations (96):** Core requirement. Without registered stations, there is no forge system. Highest score reflects this foundational necessity.

**F002 — Station profiles and visual themes (91):** Enables tier access control and per-station customization. Station profiles allow server operators to create gated forge areas.

**F003 — Catalyst items modifying weights (95):** High-impact player engagement feature. Catalysts give players agency to improve their odds, creating meaningful choices.

**F004 — Break-protection wards (94):** Protective mechanic reduces frustration. Players invest in wards to safeguard valuable items, creating an economy within the forge system.

**F005 — Per-tier pity system (93):** Player retention mechanism. Guaranteed eventual success prevents indefinite bad-luck streaks and keeps the forge feeling rewarding.

**F006 — Reforge provenance/history (92):** Accountability and transparency. History tracking enables anti-dupe verification and player satisfaction tracking.

**F007 — Per-tier cooldowns (88):** Pace control. Cooldowns prevent abuse and encourage players to use multiple stations or return later.

**F008 — Exact preview/simulation (89):** Informed decision-making. Players should know what they are risking before committing resources.

**F009 — Jackpot announcements (88):** Social engagement and excitement. Public success announcements create server community moments.

**F010 — Audit transaction ledger (87):** Operator requirement. Audit logs are essential for debugging, compliance, and detecting abuse patterns.

## Complete Feature Dataset

| ID     | Candidate                             | Class     | Score | Decision                    |
|--------|---------------------------------------|-----------|-------|-----------------------------|
| F001   | Registered forge stations             | ELIGIBLE  | 96    | SELECTED                    |
| F002   | Station profiles and visual themes    | ELIGIBLE  | 91    | SELECTED                    |
| F003   | Catalyst items modifying weights      | ELIGIBLE  | 95    | SELECTED                    |
| F004   | Break-protection wards                | ELIGIBLE  | 94    | SELECTED                    |
| F005   | Per-tier pity system                  | ELIGIBLE  | 93    | SELECTED                    |
| F006   | Reforge provenance/history           | ELIGIBLE  | 92    | SELECTED                    |
| F007   | Per-tier cooldowns                    | ELIGIBLE  | 90    | SELECTED                    |
| F008   | Exact preview/simulation              | ELIGIBLE  | 89    | SELECTED                    |
| F009   | Jackpot announcements                 | ELIGIBLE  | 88    | SELECTED                    |
| F010   | Audit transaction ledger              | ELIGIBLE  | 87    | SELECTED                    |
| F011   | Daily per-player use limits           | ELIGIBLE  | 79    | DEFERRED                    |
| F012   | Blacksmith mastery progression        | ELIGIBLE  | 78    | DEFERRED                    |
| F013   | Player forge level unlock tree        | ELIGIBLE  | 76    | DEFERRED                    |
| F014   | Server-wide forge events              | ELIGIBLE  | 72    | DEFERRED                    |
| F015   | Faction-based discounts               | ELIGIBLE  | 60    | DEFERRED                    |
| F016   | Town ownership integration            | ELIGIBLE  | 58    | DEFERRED                    |
| F017   | Biome-specific chance bonuses         | ELIGIBLE  | 64    | DEFERRED                    |
| F018   | Time-of-day chance bonuses            | ELIGIBLE  | 50    | DEFERRED                    |
| F019   | Weather chance bonuses                | ELIGIBLE  | 48    | DEFERRED                    |
| F020   | Moon-phase chance bonuses             | ELIGIBLE  | 42    | DEFERRED                    |
| F021   | NPC blacksmith integration            | ELIGIBLE  | 78    | DEFERRED                    |
| F022   | Hologram station labels               | ELIGIBLE  | 74    | DEFERRED                    |
| F023   | Boss-bar forge progress               | ELIGIBLE  | 82    | DEFERRED; title/actionbar core sufficient |
| F024   | Action-bar forge progress             | CORE      | 80    | INCLUDED CORE               |
| F025   | Cinematic camera control              | ELIGIBLE  | 35    | REJECTED risk               |
| F026   | Screen-shake simulation               | ELIGIBLE  | 30    | REJECTED accessibility/support |
| F027   | Resource-pack model integration       | ELIGIBLE  | 70    | DEFERRED                    |
| F028   | Multiple global GUI themes            | ELIGIBLE  | 81    | DEFERRED; station profile theme selected |
| F029   | Multi-page tier menu                  | CORE      | 85    | INCLUDED CORE               |
| F030   | Favorite tier shortcuts               | ELIGIBLE  | 55    | DEFERRED                    |
| F031   | Remember last selected tier           | ELIGIBLE  | 58    | DEFERRED                    |
| F032   | Quick-reforge hotkey                  | ELIGIBLE  | 45    | REJECTED safety             |
| F033   | Batch reforging                       | ELIGIBLE  | 20    | REJECTED anti-dupe complexity |
| F034   | Automatic retry until success         | ELIGIBLE  | 15    | REJECTED destructive UX      |
| F035   | Player forge queue                    | ELIGIBLE  | 40    | DEFERRED                    |
| F036   | Offline forge queue                  | ELIGIBLE  | 25    | REJECTED scope               |
| F037   | Cross-server forge queue              | ELIGIBLE  | 10    | REJECTED scope               |
| F038   | Redis synchronization                 | ELIGIBLE  | 20    | REJECTED dependency/scope    |
| F039   | SQL player-state storage             | ELIGIBLE  | 45    | DEFERRED                    |
| F040   | YAML player-state storage             | CORE      | 75    | INCLUDED CORE               |
| F041   | SQLite player-state storage           | ELIGIBLE  | 65    | DEFERRED                    |
| F042   | MySQL player-state storage            | ELIGIBLE  | 50    | DEFERRED                    |
| F043   | Web administration dashboard          | ELIGIBLE  | 20    | REJECTED scope               |
| F044   | Discord webhook audit                 | ELIGIBLE  | 70    | DEFERRED                    |
| F045   | Prometheus metrics                    | ELIGIBLE  | 40    | DEFERRED                    |
| F046   | PlaceholderAPI expansion             | ELIGIBLE  | 75    | DEFERRED                    |
| F047   | Public Bukkit forge events            | ELIGIBLE  | 83    | DEFERRED                    |
| F048   | Developer service API                 | ELIGIBLE  | 80    | DEFERRED                    |
| F049   | Custom script outcome engine          | ELIGIBLE  | 25    | REJECTED security            |
| F050   | Embedded JavaScript engine            | ELIGIBLE  | 5     | REJECTED security/dependency |
| F051   | MythicMobs reward hook               | ELIGIBLE  | 60    | DEFERRED; generic commands cover |
| F052   | ItemsAdder reward hook               | ELIGIBLE  | 68    | DEFERRED; generic commands cover |
| F053   | Oraxen reward hook                   | ELIGIBLE  | 66    | DEFERRED; generic commands cover |
| F054   | MMOItems reward hook                 | ELIGIBLE  | 70    | DEFERRED; generic commands cover |
| F055   | Nexo reward hook                     | ELIGIBLE  | 62    | DEFERRED; generic commands cover |
| F056   | EcoItems reward hook                 | ELIGIBLE  | 60    | DEFERRED; generic commands cover |
| F057   | ExecutableItems reward hook          | ELIGIBLE  | 58    | DEFERRED; generic commands cover |
| F058   | Generic required-plugin command hooks | CORE      | 85    | INCLUDED CORE               |
| F059   | Direct SMPWeapons API hook           | ELIGIBLE  | 72    | REJECTED; command hook requested |
| F060   | Vault money costs                    | CORE      | 90    | INCLUDED CORE               |
| F061   | PlayerPoints costs                   | ELIGIBLE  | 55    | DEFERRED; generic command insufficient for atomic costs |
| F062   | TokenManager costs                   | ELIGIBLE  | 50    | DEFERRED                    |
| F063   | Multiple simultaneous currencies      | ELIGIBLE  | 57    | DEFERRED                    |
| F064   | Reusable material groups              | CORE      | 85    | INCLUDED CORE               |
| F065   | Item whitelist rules                  | CORE      | 84    | INCLUDED CORE               |
| F066   | Item blacklist rules                   | ELIGIBLE  | 82    | DEFERRED; whitelist/matcher sufficient |
| F067   | Custom model data matching            | CORE      | 80    | INCLUDED CORE               |
| F068   | Lore substring matching               | CORE      | 65    | INCLUDED CORE               |
| F069   | Regex item matching                   | ELIGIBLE  | 45    | REJECTED ReDoS/support risk |
| F070   | PDC item matching                     | CORE      | 75    | INCLUDED CORE identity      |
| F071   | Durability condition matching         | ELIGIBLE  | 78    | DEFERRED                    |
| F072   | Enchantment prerequisites            | CORE      | 79    | INCLUDED CORE matcher       |
| F073   | Player level prerequisites beyond cost | ELIGIBLE  | 65    | DEFERRED                    |
| F074   | Permission-filtered outcomes          | CORE      | 81    | INCLUDED CORE               |
| F075   | World-filtered tiers                  | ELIGIBLE  | 68    | DEFERRED; station profiles cover primary need |
| F076   | Region-plugin filters                 | ELIGIBLE  | 60    | DEFERRED                    |
| F077   | Station owner permissions             | ELIGIBLE  | 55    | DEFERRED                    |
| F078   | Per-world tier configs                | ELIGIBLE  | 70    | DEFERRED                    |
| F079   | Per-station tier allowlists           | CORE      | 84    | INCLUDED selected station profiles |
| F080   | Random rotating tier availability      | ELIGIBLE  | 50    | DEFERRED                    |
| F081   | Seasonal outcome pools                | ELIGIBLE  | 66    | DEFERRED                    |
| F082   | Weighted rarity pools                 | CORE      | 85    | INCLUDED CORE               |
| F083   | Guaranteed outcome after N failures   | ELIGIBLE  | 86    | DEFERRED; bounded pity selected instead |
| F084   | Downgrade outcomes                    | CORE      | 75    | SUPPORTED by mutation config |
| F085   | No-change outcomes                    | CORE      | 70    | INCLUDED CORE               |
| F086   | Durability-damage outcomes            | CORE      | 76    | SUPPORTED by mutation extension if configured |
| F087   | Repair outcomes                       | CORE      | 78    | SUPPORTED by mutation extension if configured |
| F088   | Material transmutation outcomes       | CORE      | 82    | SUPPORTED by CREATE_ITEM    |
| F089   | Socket system                         | ELIGIBLE  | 60    | DEFERRED                    |
| F090   | Gem insertion system                  | ELIGIBLE  | 64    | DEFERRED                    |
| F091   | Item-set bonuses                      | ELIGIBLE  | 55    | DEFERRED                    |
| F092   | Custom attributes                     | CORE      | 88    | INCLUDED CORE capability-gated |
| F093   | High/custom enchant levels            | CORE      | 87    | INCLUDED CORE               |
| F094   | Curse outcomes                        | CORE      | 86    | INCLUDED CORE               |
| F095   | Item binding                          | ELIGIBLE  | 40    | DEFERRED                    |
| F096   | Soulbound result                      | ELIGIBLE  | 50    | DEFERRED                    |
| F097   | Trade restrictions                    | ELIGIBLE  | 35    | REJECTED scope              |
| F098   | Anti-dupe transaction lock            | CORE      | 100   | INCLUDED CORE safety        |
| F099   | Crash-safe persistent escrow journal   | ELIGIBLE  | 82    | DEFERRED; pending delivery included, full WAL not included |
| F100   | Locale/language packs                  | ELIGIBLE  | 78    | DEFERRED; one messages file now |

## Class Definitions

| Class     | Description                                               |
|-----------|-----------------------------------------------------------|
| CORE      | Essential infrastructure without which the plugin cannot function meaningfully |
| ELIGIBLE  | Candidate feature that is not essential but would add significant value     |
| REJECTED  | Considered but rejected; reasons vary (scope, security, complexity, risk)  |

## Decision Categories

| Decision                        | Count | Notes                                           |
|---------------------------------|-------|-------------------------------------------------|
| SELECTED                        | 10    | Implemented in v1.0.0                          |
| INCLUDED CORE                  | 15    | Already part of core infrastructure              |
| SUPPORTED                      | 5     | Available via configuration extension             |
| DEFERRED                       | 40    | Not in v1.0.0; may appear in future releases    |
| REJECTED                       | 12    | Explicitly rejected with stated reasons          |

**Total:** 100 candidates across 4 classes with 5 decision outcomes.
