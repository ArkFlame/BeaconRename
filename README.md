# FlameForge

FlameForge is a customizable Minecraft forge/reforge plugin for Spigot, Paper,
and Folia servers. Players interact with forge stations to open a GUI-based
forge menu where they reforge weapons, armor, shields, and amulets with
configured tiers, variants, costs, cooldowns, powers, and visual effects.

## Compatibility

| Platform      | Version        | Notes                                     |
|---------------|----------------|-------------------------------------------|
| Spigot/Bukkit/Paper/Folia | 1.8.8 – modern 1.21.x | Runtime capability profile; no version-specific particle branches |
| Folia         | Supported      | `folia-supported: true`, entity/region schedulers |
| Java          | 8+             | Compiled for Java 8 bytecode              |
| PacketEvents  | 2.13.0 (Spigot)| Hard dependency (external plugin)         |

Modern server capabilities (offhand API, swap-hand events, NETHERITE
materials, attribute APIs, particle/sound names) are isolated behind
runtime-detecting compatibility bridges; the plugin still runs on 1.8.8 where
those capabilities fall back gracefully. Spear material strings classify as
weapons when the running server's material resolver supports them; unavailable
strings are skipped on older runtimes, with no direct modern enum promise.

Particle compatibility is dynamic: the particle bridge selects available
capabilities and payload data types at runtime. Semantic styles provide ordered
candidate families, pattern builders calculate geometry, and network renderers
batch frames before entity-owned player scheduling sends them. See
[CONTRIBUTING](CONTRIBUTING.md) for the rules for extending this architecture.

## Build and Install

Requirements: Java 8 JDK or higher, Maven 3.x.

```bash
mvn clean install
```

The shaded JAR is `target/FlameForge-1.0.2.jar`.

1. Stop the server.
2. Place the JAR in `plugins/`. **PacketEvents must also be installed** —
   FlameForge declares it as a hard dependency.
3. Start the server. Default configuration, tier files, and equipment catalog
   are generated into `plugins/FlameForge/`.
4. Register forge stations: `/flameforge station add myforge default` while
   looking at any non-air block (default mode is `REGISTERED_ONLY`).
5. Right-click the registered block to open the forge.

## Quick Start

1. `/flameforge station add myforge default` — register a forge at the block
   you are looking at (any non-air block works).
2. `/flameforge validate` — confirm configuration is clean.
3. Open the forge by right-clicking the block, place an item in the input
   slot (22), and confirm (slot 31). The item's current forged identity
   determines the exact next tier in its category's progression.
4. `/flameforge testitem weapon_tier1 bloodletter` — spawn a forged test item
   to check variants and powers without a forge transaction.
5. `/flameforge weaponsmenu` — browse weapon progression examples and receive
   fresh forged examples without station, cost, cooldown, or history effects.

## Equipment Progression

Items are classified by category in `equipment.yml`: `weapon`, `armor`,
`shield`, and `amulet` (fallback for anything unmatched). Bundled progression
has 28 forgeable category tiers: seven each for
`weapon_tier1..7`, `armor_tier1..7`, `shield_tier1..7`, and
`amulet_tier1..7`. Weapon materials include swords, axes, bow, crossbow,
trident, mace, and spear strings when available. The seven legacy `tier1..7`
files remain readable only so old forged items keep resolving during
migration. No Miner Charm variant is bundled. Operator tier files in `tiers/`
override bundled tiers by id; a tier is forgeable only when its id is
referenced by a category progression. Incomplete progressions are validation
errors.

## Features

- **Forge menu**: 54-slot single-input GUI (input slot 22, confirm slot 31);
  no tier buttons — the item's identity drives progression; requirements,
  chances, and variants are shown on the confirm button.
- **Outcomes**: SUCCESS (variant mutation), BREAK (reset/strip, optionally
  destroy), CURSE (terminal, no further forging), with per-tier chances and
  distinct success, curse, fractured, and destroyed messages.
- **Powers**: on-hit potion/fire/heal/bleed/explosive, every-N-hit
  lightning/knockback, radial AOE fire, chain potion/damage (true A→B→C hops
  with dedupe and target caps), on-block potion/knockback/heal, passive
  potions, shift-right-click dash/heal. Activation slots include main/off
  hand, armor slots, and inventory; passive duration ticks are internal
  refresh leases, not visible effect lifetimes.
- **Particles**: per-power particle candidates with semantic fallbacks;
  chain trails use straight parent-hop frames, bleed uses redstone block-break,
  red dust, and CRIT, and cosmetic failures never abort a power. Runtime
  particle names, keys, and typed payloads are selected from the server's
  capabilities rather than version branches.
- **Armor**: semantic damage-reduction attributes composed per damage cause,
  capped at 80%; flat attack bonus via native attributes when available.
- **Animation**: PacketEvents fake-item orbit/rise with double spiral, trail,
  aura, five-point star reveal; outcome-themed palettes (electric, swift,
  poison, contagion, bleed, explosive, heal, curse, break). No real item
  entity is spawned.
- **Economy (Vault)**: soft dependency; money requirements and costs only when
  Vault with an economy provider is installed.
- **Holograms**: FancyHolograms and DecentHolograms providers (soft
  dependencies, configurable order); skipped with a log line when absent.
- **Messages**: MiniMessage throughout, layered operator overrides.

## Commands

All commands are subcommands of `/flameforge` (aliases: `forge`, `ff`).
Admin nodes default to op and are children of `flameforge.admin`. Full
reference: [COMMANDS-AND-PERMISSIONS](docs/COMMANDS-AND-PERMISSIONS.md).

| Command                                      | Permission                        | Default |
|----------------------------------------------|-----------------------------------|---------|
| `/flameforge help [page]`                    | `flameforge.command.help`         | true    |
| `/flameforge open [player]`                  | `flameforge.command.open(.others)`| true/op |
| `/flameforge history [player]`               | `flameforge.command.history(.others)` | true/op |
| `/flameforge tiers [page]`                   | `flameforge.command.tiers`        | op      |
| `/flameforge tier info <tier>`               | `flameforge.command.tier.info`    | op      |
| `/flameforge preview <tier> [material]`      | `flameforge.command.preview`      | op      |
| `/flameforge testitem <tier> <variant> [material]` | `flameforge.command.testitem` | op |
| `/flameforge weaponsmenu`                          | `flameforge.command.weaponsmenu` | op |
| `/flameforge reload` / `/flameforge validate`| `flameforge.command.reload` / `.validate` | op |
| `/flameforge station add|remove|list|info|teleport` | `flameforge.command.station.*` | op |
| `/flameforge tp <id>`                        | `flameforge.command.station.teleport` | op |
| `/flameforge setup tier create|clone`        | `flameforge.command.setup.tier`   | op      |

## Configuration Files

| File                    | Purpose                                        |
|-------------------------|------------------------------------------------|
| `config.yml`            | Root settings, stations mode, announcements, holograms, forge runtime |
| `equipment.yml`         | Categories, materials, tier progression, legacy tier ids |
| `tiers/*.yml`           | Tier definitions (schema v2: requirements, chances, break/curse, animation, variants, powers) |
| `messages.yml`          | MiniMessage strings (operator overrides bundled defaults) |
| `menus.yml`             | GUI layout and styling                         |
| `station-profiles.yml`  | Station behavior profiles (max tier, permissions) |
| `stations/<id>.yml`     | Registered forge stations (runtime-created)    |

See [CONFIGURATION](docs/CONFIGURATION.md) for schemas and validation rules.

## Optional Dependencies

| Plugin          | Purpose                                 | Hook Type       |
|-----------------|-----------------------------------------|-----------------|
| Vault           | Economy (money requirements/costs)      | Soft dependency |
| FancyHolograms  | Station holograms (provider order 1)    | Soft dependency |
| DecentHolograms | Station holograms (provider order 2)    | Soft dependency |
| PacketEvents    | Forge animation fake item packets       | Hard dependency |

FlameForge operates without Vault and without hologram providers (money
requirements report unavailable; holograms are skipped). There is no
PlaceholderAPI hook — all placeholders are the plugin's own MiniMessage
template variables.

## Documentation

- [CONFIGURATION](docs/CONFIGURATION.md) — config files, equipment catalog, tier schema, messages
- [COMMANDS-AND-PERMISSIONS](docs/COMMANDS-AND-PERMISSIONS.md) — command reference, permissions, tab completion
- [OUTCOMES-AND-HOOKS](docs/OUTCOMES-AND-HOOKS.md) — outcomes, power semantics, particles, armor, hooks
- [ARCHITECTURE](docs/ARCHITECTURE.md) — runtime compatibility and service architecture
- [CONTRIBUTING](CONTRIBUTING.md) — compatibility-safe contribution rules
- [ADMIN-GUIDE](docs/ADMIN-GUIDE.md) — build/install, editing, validation, compatibility, smoke tests
