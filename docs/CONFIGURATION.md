# FlameForge Configuration Reference

This document describes the configuration files shipped with FlameForge 1.0.2
and how the runtime loads them. Every file described below is real and shipped
in the JAR; the operator copy lives in `plugins/FlameForge/`.

## File Overview

| File                      | Bundled | Operator copy                  | Purpose                                        |
|---------------------------|---------|--------------------------------|------------------------------------------------|
| `config.yml`              | Yes     | `plugins/FlameForge/config.yml`  | Root plugin settings (schema-version 2)        |
| `equipment.yml`           | Yes     | `plugins/FlameForge/equipment.yml` | Equipment categories and tier progression   |
| `tiers/*.yml`             | Yes     | `plugins/FlameForge/tiers/`    | Tier definitions (schema-version 2)            |
| `messages.yml`            | Yes     | `plugins/FlameForge/messages.yml` | MiniMessage strings for commands and menus  |
| `menus.yml`               | Yes     | `plugins/FlameForge/menus.yml` | GUI layout and styling                         |
| `station-profiles.yml`    | Yes     | `plugins/FlameForge/station-profiles.yml` | Forge station behavior profiles      |
| `stations/<id>.yml`       | No      | runtime-created                | One file per registered forge station          |

`config.yml`, `menus.yml`, and `equipment.yml` are merged over their bundled
baseline with a recursive merge: operator values replace leaf values and
override whole maps only when the corresponding baseline key is not itself a
map. Unknown or malformed operator content is reported through validation.

## config.yml (root settings)

Bundled defaults (schema-version 2):

- `enabled` — plugin on/off switch.
- `station-mode` — `REGISTERED_ONLY` (only registered forges open) or
  `ANY_BLOCK` (any non-air block is a forge). Default `REGISTERED_ONLY`.
- `audit-queue-capacity`, `audit.enabled`, `audit.folder`, `audit.max-file-age-days` — audit log settings.
- `chance-decimals`, `chance-display-decimals` — chance precision.
- `unsafe-enchants` — enchantments that cannot be forged (e.g. `CURSE_OF_VOIDING`).
- `item-groups` — named material lists for filtering.
- `item-display-names` — display-name overrides per material.
- `announcements.global` / `announcements.station` — title/subtitle broadcasts for forge results.
- `animation-profile`, `menu-profile` — default profile names.
- `cost-display`, `cost-colors` — cost formatting.
- `forge.passive-refresh-ticks`, `forge.power-cooldown-max-entries` (default 4096),
  `forge.reject-foreign-persistent-data`, `forge.menu.profile` — power/cooldown and menu runtime settings.
- `holograms` — `enabled`, `provider-order` (default `FancyHolograms`,
  `DecentHolograms`), `offset-y`, `transparent-background`, `lines` (MiniMessage
  lines with `%forge_id%` placeholder).

## equipment.yml (categories and progression)

`schema-version: 1`. The bundled file defines four categories:

| Category | ID      | Fallback | Progression                                  | Materials                          |
|----------|---------|----------|----------------------------------------------|------------------------------------|
| Weapon   | `weapon`| no       | `weapon_tier1` … `weapon_tier7`              | swords, axes, bow, crossbow, trident, mace |
| Armor    | `armor` | no       | `armor_tier1` … `armor_tier7`                | helmets, chestplates, leggings, boots, turtle helmet, elytra |
| Shield   | `shield`| no       | `shield_tier1` … `shield_tier7`              | `SHIELD`                           |
| Amulet   | `amulet`| yes      | `amulet_tier1` … `amulet_tier7`              | none (fallback)                    |

### Amulet fallback

Exactly one category must be the fallback and it must be `amulet`. Materials
that do not match any non-fallback category resolve to the fallback category,
so an amulet progression applies to any item the other categories do not claim.
The fallback category has an empty `materials` list.

### legacy-tier-ids

`legacy-tier-ids: [tier1 … tier7]` lists the pre-category tier identities. They
remain readable so old forged items (whose stored identity references a legacy
tier id) still resolve during migration. They are not part of any category
progression; new forging uses the category tier ids. If an operator overlay
contains `legacy-tier-ids`, validation reports a warning that legacy tier IDs
are ignored in operator files (the bundled list is used).

### Operator overlay and tier resolution

- The bundled `equipment.yml` is the baseline; `plugins/FlameForge/equipment.yml`
  (if present) is merged over it.
- Tiers load in order: bundled legacy `tier1…tier7`, then bundled category
  tiers referenced by progression (`weapon_tier1…`, `armor_tier1…`,
  `shield_tier1…`, `amulet_tier1…`), then operator files from
  `plugins/FlameForge/tiers/` (sorted by file name).
- **Operator tier override by ID**: an operator tier file whose `id` matches an
  already-loaded tier replaces that tier entirely. A file with a new id is
  added. A tier file that fails parsing is skipped with a warning and does not
  replace anything.
- **Forgeability**: a tier participates in forging only when its id appears in
  a category `progression`. Custom tiers must be referenced by progression to
  be forgeable.
- **Incomplete progression is a validation error**: each category must list
  exactly 7 tiers. Validation rejects a category whose progression has the
  wrong size, references an unknown tier id, has a tier whose `level` does not
  match its position (position 1 = level 1), or shares a tier id with another
  category.

### First-run bootstrap

If `plugins/FlameForge/tiers/` does not exist on startup, the plugin creates it
and copies the bundled tier files and `equipment.yml` into the data folder.
Existing files are never overwritten.

## tiers/*.yml (tier definitions)

Schema-version 2. Structure of a tier file:

```yaml
schema-version: 2
id: weapon_tier1          # tier identity (referenced by category progression)
level: 1                  # progression position (1-based)
enabled: true
display:
  name: "..."             # MiniMessage
  lore: [...]             # MiniMessage
cooldown-seconds: 5       # station cooldown in SECONDS
input:
  allowed-groups: [WEAPON]  # WEAPON | ARMOR | SHIELD | AMULET
  denied-materials: []
requirements:
  combine: ALL
  xp:      { enabled: true,  amount: 10 }
  money:   { enabled: false, amount: 1000.00 }
  items:   { enabled: false, required: [...] }
chances:
  success: "90.0"         # percent
  break: "5.0"
  curse: "5.0"
break:                    # break outcome behavior
  reset-tier: true
  target-tier: 0
  destroy-item: false     # amulet tier 1 ships with true
  result-display-name: "..."
  result-lore: [...]
  reset-display-name: true
  reset-lore: true
  reset-enchantments: true
  reset-attributes: true
  reset-powers: true
  reset-custom-model-data: true
curse:                    # curse outcome behavior
  display-name: "..."
  lore: [...]
  enchantment-candidates: [VANISHING_CURSE, CURSE_OF_VANISHING]
animation:
  duration-ticks: 20
  interval-ticks: 4
  charge-sound: { candidates: [...], volume: 1.0, start-pitch: 0.50, end-pitch: 2.00 }
  charge-particle: { candidates: [FLAME], count: 12, radius: 1.20 }
  impact-particle: { candidates: [CRIT, FLAME], material-candidates: [ANVIL] }
  success: { sound-candidates: [...], particle-candidates: [...], title: "...", subtitle: "..." }
  break:   { sound-candidates: [...], particle-candidates: [...], title: "...", subtitle: "..." }
  curse:   { sound-candidates: [...], particle-candidates: [...], title: "...", subtitle: "..." }
variants:
  variant_id:
    weight: "34.0"
    applicable-groups: [WEAPON]
    display-name: "..."     # MiniMessage, supports %base_name%
    lore: [...]
    enchantments: []
    attributes: []
    powers: [ ... ]         # see below
```

### Cooldown units

- Tier-level `cooldown-seconds` is authored in seconds; `/flameforge tier info`
  displays it as seconds.
- Power-level `cooldown-ticks` is authored in ticks (20 ticks = 1 second) and
  enforced internally as ticks. The shipped tier files pair the two: a variant
  lore line such as `Cooldown: 2s` corresponds to `cooldown-ticks: 40`.

### Power definitions (inside `variants.<id>.powers`)

```yaml
- id: bloodletter_bleed
  type: ON_HIT_BLEED
  cooldown-ticks: 40
  chance: "0.12"
  damage-amount: "0.5"
  pulse-count: 2
  pulse-interval-ticks: 12
  particle-candidates: [CRIT, HEART]
```

Supported `type` values and their key fields:

| Type                      | Fields used                                    |
|---------------------------|------------------------------------------------|
| `ON_HIT_POTION`           | effect-candidates, duration-ticks, amplifier, chance, cooldown-ticks |
| `ON_HIT_FIRE`             | fire-ticks, chance, cooldown-ticks             |
| `ON_HIT_HEAL`             | heal-amount, chance, cooldown-ticks            |
| `PASSIVE_POTION`          | effect-candidates, duration-ticks, amplifier, activation-slots |
| `SHIFT_RIGHT_CLICK_DASH`  | horizontal-strength, vertical-strength, cooldown-ticks |
| `SHIFT_RIGHT_CLICK_HEAL`  | heal-amount, cooldown-ticks                    |
| `EVERY_N_HIT_LIGHTNING`   | hit-interval, chance, cooldown-ticks           |
| `EVERY_N_HIT_KNOCKBACK`   | hit-interval, chance, horizontal/vertical-strength, cooldown-ticks |
| `ON_HIT_AOE_FIRE`         | fire-ticks, radius, max-targets, chance, cooldown-ticks |
| `ON_HIT_BLEED`            | damage-amount, pulse-count, pulse-interval-ticks, chance, cooldown-ticks |
| `ON_HIT_EXPLOSIVE`        | damage-amount, radius, max-targets, primary-knockback-multiplier, secondary-damage-multiplier, chance, cooldown-ticks |
| `ON_HIT_CHAIN_POTION`     | effect-candidates, duration-ticks, amplifier, radius, max-targets, chain-delay-ticks, trail-points, chance, cooldown-ticks |
| `ON_HIT_CHAIN_DAMAGE`     | damage-amount, radius, max-targets, chain-delay-ticks, trail-points, chance, cooldown-ticks |
| `ON_BLOCK_POTION`         | effect-candidates, duration-ticks, amplifier, chance, cooldown-ticks |
| `ON_BLOCK_KNOCKBACK`      | horizontal/vertical-strength, chance, cooldown-ticks |
| `ON_BLOCK_HEAL`           | heal-amount, chance, cooldown-ticks            |

`activation-slots` accepts `MAIN_HAND`, `OFF_HAND`, `HEAD`, `CHEST`, `LEGS`,
`FEET`, `INVENTORY`. `max-targets` is validated to 1..16; shipped chain powers
use caps of 8 and 10 (e.g. `weapon_tier3` chain potion at 8, `weapon_tier7`
chain damage at 10), radial powers ship at 4–6. All power values are validated
at load time; invalid values fail the tier file.

## messages.yml (layered defaults)

All strings use MiniMessage. Key groups: `startup`, `command`, `help`, `open`,
`forge-interact`, `reload`, `validate`, `tiers`, `tier-info`, `preview`,
`testitem`, `history`, `station*`, `tp`, `setup*`, `forge` (outcome titles),
`cooldown`, `cost`, `validation`, `announcements`, `menu`, `delivery`.

Layering: the bundled `messages.yml` inside the JAR is the default. Operator
overrides in `plugins/FlameForge/messages.yml` take precedence per key; any key
missing from the operator file falls back to the bundled value. Unknown
placeholders in messages are logged once and rendered empty.

Supported global placeholders: `%player_name%`, `%player%`, `%display_name%`,
`%world%`, `%world_name%`, `%online%`, `%online_players%`, `%max_players%`,
`%health%`, `%health_points%`, `%food%`, `%xp_level%`, `%item_name%`,
`%item_in_hand%`. Command-specific placeholders (`%tier_id%`, `%station_id%`,
`%permission%`, etc.) are documented per command in
[COMMANDS-AND-PERMISSIONS.md](COMMANDS-AND-PERMISSIONS.md).

## menus.yml (GUI layout)

Schema-version 2. The `default` profile is a 54-slot menu with `input` at slot
22 and `confirm` at slot 31. `confirm.items` defines empty / blocked / ready
states (materials with version fallbacks, glow, name, lore with `%tier_line%`,
`%requirements%`, `%chances%`, `%variants%`). `dynamic-lines` renders tier and
requirement rows. Additional profiles can be added and referenced from station
profiles or `config.yml` `menu-profile`.

## station-profiles.yml

Each profile defines `station-id`, `max-tier` (-1 = unlimited), `permissions`
(player permission requirements), `menu`, `animation`, `announcement-radius`.
Bundled profiles: `default` (max-tier -1), `basic` (max-tier 3), `premium`
(requires `flameforge.premium`), `admin` (requires `flameforge.admin`),
`compact-profile` (max-tier 5, `compact` menu). `flameforge.premium` is a
station profile permission, not a plugin.yml permission node.

## Validation and reload behavior

- `/flameforge validate` parses everything without applying; it reports errors
  and warnings per file and field.
- `/flameforge reload` re-runs the full load; if validation finds errors the
  reload is rejected and the previous configuration stays active
  (`reload.validation-rejected`).
- A failed startup is retryable when the failure component is file-based:
  correct the reported file and run `/flameforge reload`.
