# FlameForge Project Specification

This document constitutes the acceptance contract for FlameForge version 1.0.0.

## Overview

FlameForge is a beacon-based forge/reforge system for Minecraft servers. Players place items into a GUI at registered beacon blocks and receive random outcomes based on weighted probability tables.

## Implemented Features

The following ten features are selected for inclusion in version 1.0.0:

### F001 — Registered Forge Stations

Players interact only with beacon blocks that have been explicitly registered via the `/flameforge station add` command. Stations store world, coordinates, and profile assignment. Station data persists in `stations.yml`.

**Acceptance criteria:**
- Station registration requires player proximity to beacon (6 blocks).
- Duplicate coordinates or IDs are rejected.
- Stations can be removed via command.
- Station list command shows ID, world, coordinates, and profile.

### F002 — Station Profiles and Visual Themes

Station profiles define per-station tier access and permission requirements. Profiles are defined in `config.yml` under the `stations` section.

**Acceptance criteria:**
- Profile defines `max-tier` to limit accessible tiers.
- Profile defines `permissions` list for access control.
- Profile can reference menu and animation profiles.

### F003 — Catalyst Items Modifying Weights

Catalyst items, placed in the catalyst slot, modify outcome weights. Catalysts are defined in `config.yml` under `catalysts`.

**Acceptance criteria:**
- Catalyst specifies `material`, `name`, `lore`, `chance-modifier`, and `consume` flag.
- `protected-outcomes` list specifies outcomes the catalyst cannot trigger.
- Consuming the catalyst removes it after forge execution.

### F004 — Break-Protection Wards

Ward items, placed in the ward slot, protect the input item from BREAK outcomes. Wards are defined in `config.yml` under `wards`.

**Acceptance criteria:**
- Ward specifies `material`, `name`, `lore`, and `protected-outcomes` list.
- `protect_all: true` protects against all BREAK outcomes.
- `convert_to_unchanged` list specifies outcome types converted to RETURN_UNCHANGED when ward is active.
- Ward is consumed after protection triggers.

### F005 — Per-Tier Pity System

Pity system increments a counter on each failed (BREAK) outcome. When the counter reaches the configured threshold, a bonus weight is applied to subsequent rolls.

**Acceptance criteria:**
- Tier definition includes `pity.enabled`, `pity.threshold`, `pity.bonus-weight`.
- Pity counter persists per-player in YAML player data.
- Counter resets on successful non-BREAK outcome.
- Bonus weight is applied to the chance table on subsequent rolls.

### F006 — Reforge Provenance/History

Player reforge history is tracked and viewable. History includes station, tier, outcome, and timestamp.

**Acceptance criteria:**
- History is stored per-session and logged via `ForgeHistory`.
- `/flameforge history [player]` shows recent outcomes.
- History is not persisted beyond the session in the initial release.

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
- Item sets and gems (F089–F092)
- Locale packs beyond single messages file (F100)

## Tier Deletion Behavior

Deleting a tier file removes that tier from the active configuration. Players who have that tier selected will see the tier disappear from their menu on next render. Deleting all tier files does not disable the plugin; the forge menu opens with an empty tier list.

## API Contract

The plugin exposes no public API in version 1.0.0. External integration occurs through:

- Command-based hooks (outcome `COMMANDS` type)
- Vault economy (if present)
- SMPWeapons detection (if present)

## Version Constraints

- Schema version: 1
- Minimum Spigot API: 1.13
- Target Java: 8
- Folia: supported via entity scheduler bridge
