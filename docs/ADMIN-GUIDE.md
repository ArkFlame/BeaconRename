# FlameForge Administrator Guide

## Build and Install

Requirements: JDK 8+ and Maven 3.x.

```bash
mvn clean install
```

The shaded JAR is written to `target/FlameForge-1.0.2.jar`.

Installation:

1. Stop the server.
2. Place `FlameForge-1.0.2.jar` into `plugins/`. **PacketEvents is a hard
   dependency** — the PacketEvents plugin must also be installed or FlameForge
   will not load.
3. Start the server. On first run FlameForge creates `plugins/FlameForge/`
   and copies the bundled `config.yml`, `equipment.yml`, `menus.yml`,
   `messages.yml`, `station-profiles.yml` and the default tier files into
   `tiers/`.
4. Register forge stations with `/flameforge station add <id> [profile]` while
   looking at a non-air block (default `station-mode` is `REGISTERED_ONLY`).

## Runtime Commands

| Command                                | Purpose                                              |
|----------------------------------------|------------------------------------------------------|
| `/flameforge validate`                 | Parse/validate all configuration without applying    |
| `/flameforge reload`                   | Re-load configuration; rejected if validation fails  |
| `/flameforge tiers [page]`             | List loaded tiers                                    |
| `/flameforge tier info <tier>`         | Tier details (level, enabled, cooldown, requirements, chances, variants) |
| `/flameforge station list|info|tp`     | Manage and navigate stations                         |
| `/flameforge testitem <tier> <variant> [material]` | Spawn a forged test item                 |
| `/flameforge setup tier create|clone`  | Scaffold new tier files                              |

All admin commands require their `flameforge.command.*` permission (op by
default) or `flameforge.admin`. See
[COMMANDS-AND-PERMISSIONS.md](COMMANDS-AND-PERMISSIONS.md) for the full table.

### reload vs validate

- `validate` parses and validates without changing anything; it reports per
  file/field errors and warnings.
- `reload` re-runs the whole load. If validation reports errors, the reload is
  rejected and the previous configuration stays active. After a retryable
  startup failure (bad file), fix the reported file and run `/flameforge
  reload` to retry startup; non-retryable failures require a restart.

## Editing Tiers and Equipment

Tier files live in `plugins/FlameForge/tiers/` (schema-version 2). Key rules:

- Operator files **override by id**: a file whose `id` matches a bundled tier
  replaces it; a new id is added. Files that fail parsing are skipped with a
  warning and do not replace anything.
- A tier is **forgeable only when its id appears in an equipment.yml category
  `progression`**. Custom tiers must be added to a progression to be usable.
- Each category progression must contain exactly 7 tiers. Validation errors:
  incomplete progression size, unknown tier id in progression, tier `level`
  not matching its progression position, tier id shared by multiple
  categories, more than one fallback category, fallback not `amulet`, or
  material listed in multiple non-fallback categories.
- `legacy-tier-ids` in the operator `equipment.yml` is ignored (warning); it
  exists only to keep old forged identities readable during migration.
- Tier-level cooldown is `cooldown-seconds`; power cooldowns are
  `cooldown-ticks` (ticks). Shipped variant lore shows seconds (40 ticks = 2s).
- `enabled: false` disables a tier while keeping it in the progression.

After editing, run `/flameforge validate`, then `/flameforge reload`.

## The testitem Workflow

`/flameforge testitem <tier> <variant> [material]` creates a fresh forged test
item (new forge id) and returns it to the player — no forge station, no costs,
no cooldown, no history. Material fallbacks per category: weapon →
NETHERITE_SWORD/DIAMOND_SWORD/IRON_SWORD, armor →
NETHERITE_CHESTPLATE/DIAMOND_CHESTPLATE/IRON_CHESTPLATE, shield → SHIELD,
amulet/uncategorized → NETHERITE_INGOT/DIAMOND/EMERALD/WOOL (first material
present on the running server). The material must match the tier's category and
the variant must be eligible for it. Passive powers on the new item activate
immediately (passive refresh runs after delivery).

This is the fastest way to verify a new variant/power/attribute combination
before players can roll it.

## Customizing Messages

Edit `plugins/FlameForge/messages.yml`. Operator values override the bundled
defaults per key; unset keys fall back to the bundled message. All strings are
MiniMessage. Command-specific keys (e.g. `testitem.*`, `tier-info.*`,
`station-add.*`, `help.descriptions.*`) are documented in the source file
itself. Unknown placeholders render empty and are logged once.

## Holograms

`holograms.enabled`, `provider-order` (FancyHolograms, DecentHolograms),
`offset-y`, `transparent-background` and `lines` are configured in config.yml.
If neither hologram plugin is installed, FlameForge logs
"no supported provider" and skips station holograms — the forge still works.

## Compatibility Boundary

- Compiled for Java 8 bytecode (`maven.compiler.release=8`).
- Built against the Spigot 1.8.8 API (`spigot-api 1.8.8-R0.1-SNAPSHOT`,
  provided). The plugin.yml declares `api-version: 1.13` and
  `folia-supported: true`.
- Modern server capabilities are isolated behind compatibility bridges and
  resolved at runtime, never compiled in:
  - `EquipmentBridge` — offhand read via reflective
    `Player.getItemInOffhand()` (AIR fallback on 1.8); `PlayerSwapHandItemsEvent`
    registered reflectively only when the class exists.
  - `MaterialResolver` — alias tables (e.g. `golden_sword` →
    `GOLDEN_SWORD`/`GOLD_SWORD`) pick the first material present at runtime;
    NETHERITE materials simply resolve to nothing on old servers.
  - `AttributeBridge` — modern attribute APIs only when available; otherwise
    `ATTACK_DAMAGE_FLAT` is applied event-side.
  - Particles/sounds — per-server candidate lists (modern name first, legacy
    fallback second); unresolved cosmetics are skipped, never fatal.
- **This compatibility boundary does not claim that every runtime/version has
  been manually executed** — smoke-test your server version after deploying
  (see below).

## User-Owned Runtime Smoke Test

These checks are executed by the server owner on their own runtime; the
plugin's automated tests do not cover live server execution:

1. Start the server, confirm "FlameForge" enables and the startup summary
   shows the expected tier counts and hologram/provider state.
2. `/flameforge validate` — expect no errors.
3. `/flameforge station add myforge default` at a block, then right-click it —
   the forge GUI opens.
4. Place a forgeable item in slot 22 and confirm the forge executes (or
   shows a requirements/chances panel).
5. `/flameforge testitem weapon_tier1 bloodletter` — the returned item should
   show the Bloodletter name/lore and bleed on hit.
6. Equip a passive-power item (e.g. `amulet_tier1` Curative) and confirm the
   potion effect appears; sneak + right-click a dash item to test
   shift-right-click powers.
7. Test on your lowest supported server version (e.g. 1.8.8) and your newest
   one, including Folia if used, because cosmetics and offhand behavior differ
   per version.

## Troubleshooting

- **Plugin does not enable / hard depend error** — PacketEvents is not
  installed. Install PacketEvents (Spigot 2.13.0) first.
- **Reload rejected with validation errors** — fix the reported file/field;
  the previous configuration is still active.
- **Startup failed (retryable)** — correct the reported file, run
  `/flameforge reload`; non-retryable failures need a restart.
- **Station holograms missing** — install FancyHolograms or DecentHolograms,
  or check the console line "Hologram provider: …".
- **Money requirements never met** — Vault is missing or has no registered
  economy provider; the menu reports "economy unavailable".
- **Legacy items no longer forge** — they are readable for migration, but only
  category-progression tiers are forgeable; reforge them through the current
  progression.
