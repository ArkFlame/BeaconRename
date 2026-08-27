# FlameForge Commands and Permissions

All commands are subcommands of `/flameforge` (aliases: `forge`, `ff`). The root
command requires `flameforge.use`. Every command node is READY-only: while the
plugin is loading, failed, or shutting down, commands reply with a startup
status message instead of executing.

Usage notation: `<arg>` required, `[arg]` optional, `[id|auto]` alternative.

## Command Reference

| Command                                        | Permission                        | Default | Access | Description                         |
|------------------------------------------------|-----------------------------------|---------|--------|-------------------------------------|
| `/flameforge help [page]`                      | `flameforge.command.help`         | true    | USER   | Show this help message              |
| `/flameforge open`                             | `flameforge.command.open`         | true    | USER   | Open the forge menu                 |
| `/flameforge open <player>`                    | `flameforge.command.open.others`  | op      | ADMIN  | Open the forge menu for a player    |
| `/flameforge tiers [page]`                     | `flameforge.command.tiers`        | op      | ADMIN  | List all forge tiers                |
| `/flameforge tier info <tier>`                 | `flameforge.command.tier.info`    | op      | ADMIN  | Show tier details                   |
| `/flameforge preview <tier> [material]`        | `flameforge.command.preview`      | op      | ADMIN  | Preview forge outcomes              |
| `/flameforge history`                          | `flameforge.command.history`      | true    | USER   | View your forge history             |
| `/flameforge history <player>`                 | `flameforge.command.history.others`| op     | ADMIN  | View another player's history       |
| `/flameforge tp <id>`                          | `flameforge.command.station.teleport` | op | ADMIN | Teleport to a forge station       |
| `/flameforge station add [id|auto] [profile]`  | `flameforge.command.station.add`  | op      | ADMIN  | Add a new forge station             |
| `/flameforge station remove <id>`              | `flameforge.command.station.remove`| op     | ADMIN  | Remove a forge station              |
| `/flameforge station list [page]`              | `flameforge.command.station.list` | op      | ADMIN  | List all forge stations             |
| `/flameforge station info <id>`                | `flameforge.command.station.info` | op      | ADMIN  | Show station details                |
| `/flameforge station teleport <id>`            | `flameforge.command.station.teleport` | op | ADMIN | Teleport to a forge station       |
| `/flameforge reload`                           | `flameforge.command.reload`       | op      | ADMIN  | Reload configuration                |
| `/flameforge validate`                         | `flameforge.command.validate`     | op      | ADMIN  | Validate configuration              |
| `/flameforge testitem <tier> <variant> [material]` | `flameforge.command.testitem` | op | ADMIN | Test forge item mutations        |
| `/flameforge weaponsmenu`                         | `flameforge.command.weaponsmenu` | op | ADMIN | Browse forged weapon examples   |
| `/flameforge setup tier create <id> <level>`   | `flameforge.command.setup.tier`   | op      | ADMIN  | Create a new tier                   |
| `/flameforge setup tier clone <source> <id> <level>` | `flameforge.command.setup.tier` | op | ADMIN | Clone an existing tier          |

`station teleport <id>` is an alias of `tp <id>` and is hidden from the help
tree (only `tp` is listed).

### Command details

- **help** — permission-filtered help. Root help lists the immediate
  subcommands grouped under General / Forging / Forge Management /
  Administration; `/flameforge help <group>` navigates into one group (e.g.
  `forging`, `forge-management`, `administration`). Unknown paths render
  `help.unknown-path`. Entries carry hover text and click-to-insert
  suggestions.
- **open** — player-only; the command resolves the forge station the *target
  player* is currently looking at (the sender without a target, or the named
  player with `flameforge.command.open.others`) and opens that player's forge
  menu. Errors: `open.no-target` (player offline), `open.no-forge-target`
  (target not looking at a registered forge), forge not found, station profile
  missing, missing station permission (`station-permission-required`), no
  allowed tier at the station.
- **tiers** — paginated list of loaded tiers with level.
- **tier info** — shows level, enabled, cooldown in seconds
  (`tier-info.cooldown`, `cooldown-seconds`), requirement combine mode, XP /
  money / item requirements, success/break/curse chances, and variants with
  weight.
- **preview** — player-only, READY-only. Resolves the tier, then uses the held
  item (`preview.no-item` if nothing held), optionally replaced by
  `[material]` (`preview.unknown-material`). Shows the first eligible variant
  (`preview.variant`) and the result material (`preview.material`). No
  transaction occurs.
- **history** — shows the player's header and current tier. The full history
  log is not yet implemented (the message `history.not-implemented` is
  rendered after the current tier).
- **tp / station teleport** — player-only, READY-only; teleports asynchronously
  and reports scheduler rejections / world-unloaded / teleport-rejected
  outcomes.
- **station add** — player-only, READY-only. While looking at a non-air block,
  registers a forge. `[id]` is validated to letters, numbers, underscore and
  hyphen; `auto` (or omitting the id) generates a unique id (fails after eight
  attempts). `[profile]` defaults to `default`. Guards: duplicate id,
  duplicate location, storage-conflict (station file exists but was not
  loaded), persistence failure (station not registered if the file cannot be
  saved).
- **station remove / list / info** — remove requires the id; list is paginated;
  info shows world, location, profile, max tier.
- **reload** — READY-only applies asynchronously. If validation finds errors
  the reload is rejected and the previous configuration remains active. If the
  current state is a retryable startup failure, reload retries startup
  (`startup-retry-started`). Guards against concurrent reloads.
- **validate** — READY-only parse-only validation; never applies changes.
- **testitem** — see the dedicated section below.
- **weaponsmenu** — see the dedicated section below.
- **setup tier create / clone** — create writes a new empty tier file with the
  given id and level (no default outcomes — edit the file); clone copies an
  existing tier file to a new id/level. Both fail on duplicate ids and
  non-numeric levels.

## /flameforge testitem

`/flameforge testitem <tier> <variant> [material]`

- **Permission**: `flameforge.command.testitem` (op; child of `flameforge.admin`).
- **Sender**: player-only. **State**: READY-only.
- **Semantics**: no transaction — the command creates a *new* test item with a
  fresh forge id and hands it to the player. No economy, cooldown, station, or
  history is involved. Passive powers are refreshed after delivery so the item
  takes effect immediately.

Argument handling:

1. `<tier>` must be a loaded tier id (`testitem.tier-not-found`).
2. `<variant>` must exist in that tier (`testitem.variant-not-found`).
3. `[material]` is resolved through `MaterialResolver` (aliases like
   `golden_sword` → `GOLDEN_SWORD`/`GOLD_SWORD`; first runtime-present
   candidate wins). Unknown or AIR material → `testitem.material-unavailable`.
   When omitted, a category-based fallback is used:
   - weapon → `NETHERITE_SWORD`, `DIAMOND_SWORD`, `IRON_SWORD`
   - armor → `NETHERITE_CHESTPLATE`, `DIAMOND_CHESTPLATE`, `IRON_CHESTPLATE`
   - shield → `SHIELD`
   - amulet or uncategorized → `NETHERITE_INGOT`, `DIAMOND`, `EMERALD`, `WOOL`
   (first present on the runtime server wins).
4. If the tier belongs to a category, the material must belong to the same
   category (`testitem.material-category-mismatch`).
5. The variant must be eligible for the material (`testitem.variant-ineligible`).
6. The item is mutated with the tier/variant success mutation; failure →
   `testitem.mutation-failed`.

On success the item is returned to the player and
`testitem.success` (tier/variant/material) is shown.

## /flameforge weaponsmenu

`/flameforge weaponsmenu`

- **Permission**: `flameforge.command.weaponsmenu` (op; child of `flameforge.admin`).
- **Sender**: player-only. **State**: READY-only. No arguments.
- **Semantics**: opens a paginated preview menu of forged weapon examples. Each
  page lists the variants of the weapon-category tier progression (28 per
  page); previews are built with the same shared `ForgeExampleItemService`
  used by `/flameforge testitem`. Clicking an entry grants the player a *new*
  forged example item with a fresh forge id (`weaponsmenu.given`) and refreshes
  passive powers; a failed build replies `weaponsmenu.give-failed`. No economy,
  cooldown, station, or history is involved.
- **Purpose**: admin testing aid for browsing weapon variants and handing out
  examples without a forge transaction.

### Tab completion

- Root level: permitted root subcommands matching the typed prefix, sorted
  case-insensitively.
- `testitem <…>`: arg 1 = loaded tier ids; arg 2 = variant ids of the given
  tier; arg 3 = material names. All filtered by prefix.
- `open` / `history`: online player names when the `.others` permission is held.
- `tier info`: tier ids. `preview`: tier ids then materials.
- `tp` / `station` / `setup`: station ids / station subcommands / setup tier
  subcommands respectively.
- `reload`, `validate`, `tiers`, `weaponsmenu` complete nothing.

## Permission Tree

```
flameforge.use                    (default true)  — root command usage
├─ flameforge.command.help        (true)   — /flameforge help
├─ flameforge.command.open        (true)   — /flameforge open
├─ flameforge.command.open.others (op)     — /flameforge open <player>
├─ flameforge.command.reload      (op)     — /flameforge reload
├─ flameforge.command.validate    (op)     — /flameforge validate
├─ flameforge.command.tiers       (op)     — /flameforge tiers
├─ flameforge.command.tier.info   (op)     — /flameforge tier info
├─ flameforge.command.preview     (op)     — /flameforge preview
├─ flameforge.command.history     (true)   — /flameforge history
├─ flameforge.command.history.others (op)  — /flameforge history <player>
├─ flameforge.command.station.add (op)     — /flameforge station add
├─ flameforge.command.station.remove (op)  — /flameforge station remove
├─ flameforge.command.station.list (op)    — /flameforge station list
├─ flameforge.command.station.info (op)    — /flameforge station info
├─ flameforge.command.station.teleport (op)— /flameforge tp / station teleport
├─ flameforge.command.setup.tier  (op)     — /flameforge setup tier create|clone
├─ flameforge.command.testitem    (op)     — /flameforge testitem
├─ flameforge.command.weaponsmenu (op)     — /flameforge weaponsmenu
flameforge.bypass.cost            (op)     — bypass forge costs
flameforge.bypass.cooldown        (op)     — bypass forge cooldowns
flameforge.admin                  (op)     — grants every node above (children)
```

`flameforge.admin` declares all `flameforge.command.*` nodes plus
`flameforge.use` as children. The bypass nodes are *not* children of
`flameforge.admin`. Command dispatch also accepts either the specific
permission or `flameforge.admin` (`CommandNode.isPermitted`). Player-specific
station permissions come from the station profile (`station-profiles.yml`),
not from plugin.yml.

## Message customization

Command output is driven by `messages.yml` keys (e.g. `tier-info.usage`,
`testitem.usage`, `station-add.usage`). The operator copy overlays the bundled
defaults per key. Help descriptions live under `help.descriptions.*`.
