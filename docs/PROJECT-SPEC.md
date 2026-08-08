# FlameForge Project Specification

This document constitutes the acceptance contract for FlameForge version 1.0.2.

## Overview

FlameForge is a forge/reforge system for Minecraft servers. Players place items into a GUI at registered forge blocks and receive random outcomes based on weighted probability tables.

## Implemented Features

The following features are selected for inclusion in version 1.0.2:

### F001 — Registered Forge Stations

Players interact only with forge blocks that have been explicitly registered via the `/flameforge station add` command. Any non-air block can be registered; no specific block material is required. Registered forge data stores world, coordinates, and profile assignment in individual `stations/<id>.yml` files.

**Acceptance criteria:**
- Station registration requires player proximity to target block (6 blocks).
- Station registration accepts any non-air block.
- Duplicate coordinates or IDs are rejected.
- Stations can be removed via command.
- Station list command shows ID, world, coordinates, and profile.

Registered-forge interaction is performed by right-clicking the registered block. Unregistered blocks do not open the forge in `REGISTERED_ONLY` mode.

### F002 — Station Profiles and Visual Themes

Station profiles define per-station tier access and permission requirements. Profiles are defined in `config.yml` under the `stations` section.

**Acceptance criteria:**
- Profile defines `max-tier` to limit accessible tiers.
- Profile defines `permissions` list for access control.
- Profile can reference menu and animation profiles.

### F007 — Per-Tier Cooldowns

Tiers may define a cooldown period (in seconds) after each use. Cooldowns persist in player state.

**Acceptance criteria:**
- Tier definition includes `cooldown-seconds` field.
- Cooldown expiry is checked on forge open.
- Bypass permission `flameforge.bypass.cooldown` exempts players.

### F008 — Exact Preview/Simulation

The preview command shows what outcome would occur for a given item and tier without executing the forge.

**Acceptance criteria:**
- `/flameforge preview <tier> [material]` displays the first outcome's mutation result.
- Preview uses the item's current state, not a simulated state.
- Preview is read-only; no costs are charged.

### F009 — Jackpot Announcements

Successful forge outcomes can broadcast messages to nearby players or globally.

**Acceptance criteria:**
- `announcements.global` and `announcements.station` sections in config.
- Announcement includes title and subtitle with MiniMessage formatting.
- Radius scope limits announcement to nearby players.

### F010 — Audit Transaction Ledger

All forge transactions are logged asynchronously to `data/audit/YYYY-MM-DD.jsonl`.

**Acceptance criteria:**
- Log entries include timestamp, action, actor, target, details.
- Queue capacity is configurable via `audit-queue-capacity`.
- Full queue drops oldest entries with warning logged.
- Ledger survives plugin reload.

## Tier Schema v2

Tier files use schema version 2 with these key changes:
- `level` replaces `priority` for automatic tier progression
- `requirements` section defines input item constraints
- Outcome `category` field (SUCCESS/BREAK/CURSE) is required
- Powers system for named stat bundles
- Same-material variant support

### Tier Requirements

```yaml
requirements:
  items:
    - material: DIAMOND_SWORD
      required: true
  strict-match: false
```

### Outcome Categories

| Category | Description |
|----------|-------------|
| `SUCCESS` | Item returned in modified form |
| `BREAK`  | Input item destroyed |
| `CURSE`  | Negative effect applied |

### CURSE Variants

| Type    | Effect |
|---------|--------|
| `VOID`  | Marks item with void curse |
| `DECAY` | Reduces item durability on each reforge |
| `DRAIN` | Reduces item stats |

## Powers System

Powers are named stat bundles defined in tier files:

```yaml
powers:
  blazing:
    enchants:
      fire_aspect_boost:
        name: FIRE_ASPECT
        min-level: 2
    attributes:
      damage:
        name: GENERIC_ATTACK_DAMAGE
        min-value: 3.0
        max-value: 5.0
        operation: ADD_NUMBER
```

Powers are applied via outcomes using `power: <power-id>`.

## 54-Slot Menu

The 54-slot forge menu layout:
- **Input slot** (slot 22, center): Single item input
- **Confirm button** (slot 31, bottom-center): Execute forge — lore shows tier/requirements/chances/variants

**Tier determination:** No tier buttons. The item's current identity determines current tier. The forge automatically targets the exact next configured tier.

**Removed:** Catalyst slot, ward slot, pity counter, tier selection buttons

## Excluded Features

Features not listed above are explicitly excluded from version 1.0.0 scope. This includes but is not limited to:

- Daily use limits (F011)
- Player forge level progression (F012, F013)
- Server-wide forge events (F014)
- Biome/time/weather/moon-phase chance bonuses (F015–F020)
- NPC integration (F021)
- Hologram labels (F022)
- Batch or queue reforging (F033–F037)
- SQL/MySQL storage (F039, F042)
- Web dashboard (F043)
- Custom scripting (F049, F050)
- Catalyst items (F003)
- Ward protection (F004)
- Pity system (F005)
- Reforge history (F006)
- SMPWeapons integration (F059)
- Item sets and gems (F089–F092)
- Locale packs beyond single messages file (F100)

## Tier Deletion Behavior

Deleting a tier file removes that tier from the active configuration. Players who have that tier selected will see the tier disappear from their menu on next render. Deleting all tier files does not disable the plugin; the forge menu opens with an empty tier list.

## API Contract

The plugin exposes no public API in version 1.0.2. External integration occurs through:

- Command-based hooks (outcome `COMMANDS` type)
- Vault economy (if present)

## Version Constraints

- Schema version: 2
- Minimum Spigot API: 1.13
- Target Java: 8
- Folia: supported via entity scheduler bridge
