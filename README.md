# FlameForge

FlameForge is a highly customizable Minecraft forge/reforge plugin for Spigot, Paper, and Folia servers. Players interact with forge stations to open a GUI-based forge menu where they can reforge weapons, armor, and tools with configurable outcomes, costs, cooldowns, and visual effects.

## Compatibility

| Platform      | Version         | Notes                                      |
|---------------|-----------------|--------------------------------------------|
| Spigot/Paper  | 1.8.8 - 1.21+  | API version 1.13 minimum                   |
| Folia         | Supported       | Uses entity scheduler for cross-thread ops  |
| Java          | 8+              | Compiled for Java 8 bytecode               |

## Build and Install

### Requirements

- Java 8 JDK or higher
- Maven 3.x

### Build

```bash
mvn clean package
```

The compiled JAR will be at `target/FlameForge-1.0.2.jar`.

### Installation

1. Stop the server.
2. Place the JAR in `plugins/`.
3. Start the server. Default configuration and tier files will be generated.
4. Review `plugins/FlameForge/config.yml` and `plugins/FlameForge/tiers/` directory.
5. Register forge stations using `/flameforge station add [id] [profile]`.
6. Restart or use `/flameforge reload`.

## Quick Start

1. Create forge stations where you want forges.
2. Run `/flameforge station add myforge default` while looking at any non-air block.
3. Edit tier files in `plugins/FlameForge/tiers/` to define outcomes.
4. Open the forge by right-clicking a registered forge.

Any non-air block can be registered as a forge; no specific block material is required. In `REGISTERED_ONLY` mode, players interact only with blocks registered as forges.

## Optional Dependencies

| Plugin         | Purpose                                | Hook Type        |
|----------------|----------------------------------------|------------------|
| Vault          | Economy integration (money costs)      | Soft dependency  |

FlameForge will operate without Vault. Economy features require Vault and a compatible economy provider.

## Menu

The forge GUI is a 54-slot single-input menu with these key slots:

- **Input slot (slot 22)**: Center slot — place the item to reforge.
- **Confirm button (slot 31)**: Bottom-center — executes the forge.

**Tier determination:** There are no tier buttons. The current item identity (stored on the item) determines the current tier. The forge automatically targets the exact next configured tier based on that identity. The confirm button lore shows the tier, requirements, chances, and available variants.

**Note:** Catalyst, ward, and pity UI slots have been removed. Tier requirements replace catalyst mechanics.

## Tier Schema v2

Tier files use schema version 2 with per-tier requirements:

```yaml
schema-version: 2
id: tier_1
level: 1
requirements:
  items:
    - material: DIAMOND_SWORD
      required: true
display:
  name: "<white>Common Forge"
  lore:
    - "<gray>Basic reforge"
  material: IRON_INGOT
cost:
  mode: XP_ONLY
  xp: 10
cooldown-seconds: 60
outcomes:
  success_modify:
    type: MODIFY_INPUT
    category: SUCCESS
    weight: 70
    mutation:
      same_material: true
      enchants:
        sharpness_1:
          name: DAMAGE_ALL
          min-level: 1
  break_item:
    type: BREAK
    category: BREAK
    weight: 25
  curse_drain:
    type: CURSE
    category: CURSE
    weight: 5
    curse:
      type: DRAIN
```

### Tier Requirements

| Field        | Type   | Description |
|--------------|--------|-------------|
| `items`      | List   | Required items to use this tier |
| `items[].material` | Material | Item material |
| `items[].required` | Boolean | Must be present in input |

### Outcome Categories

| Category | Description |
|----------|-------------|
| `SUCCESS` | Item returned in modified form |
| `BREAK`  | Input item destroyed |
| `CURSE`  | Negative effect applied |

### Tier Levels

Tiers have a `level` field instead of `priority`. Automatic next-tier progression uses level ordering.

## Commands

| Command                            | Description                          | Permission (default)      |
|------------------------------------|--------------------------------------|--------------------------|
| `/flameforge help [page]`          | Show help menu                       | `flameforge.use` (all)   |
| `/flameforge open [player]`        | Open forge GUI                       | `flameforge.command.open` (all) |
| `/flameforge reload`               | Reload configuration                  | `flameforge.command.reload` (op) |
| `/flameforge validate`             | Validate configuration files            | `flameforge.command.validate` (op) |
| `/flameforge tiers [page]`         | List all configured tiers            | `flameforge.command.tiers` (op) |
| `/flameforge tier info <tier>`     | Show tier details                    | `flameforge.command.tier.info` (op) |
| `/flameforge preview <tier> [mat]` | Preview outcome for held item        | `flameforge.command.preview` (op) |
| `/flameforge history [player]`      | View reforge history                 | `flameforge.command.history` (all) |
| `/flameforge station add [id] [profile]` | Register a forge       | `flameforge.command.station.add` (op) |
| `/flameforge station remove <id>`  | Remove a station                     | `flameforge.command.station.remove` (op) |
| `/flameforge station list [page]`  | List all stations                    | `flameforge.command.station.list` (op) |
| `/flameforge station info <id>`    | Show station details                 | `flameforge.command.station.info` (op) |
| `/flameforge station teleport <id>` | Teleport to station                  | `flameforge.command.station.teleport` (op) |
| `/flameforge setup tier create <id> <level>` | Create empty tier     | `flameforge.command.setup.tier` (op) |
| `/flameforge setup tier clone <source> <id> <level>` | Clone existing tier | `flameforge.command.setup.tier` (op) |

## Configuration Files

| File                    | Purpose                                      |
|-------------------------|----------------------------------------------|
| `config.yml`            | Root plugin settings, announcements |
| `stations/<id>.yml`     | Individual station files (one per station)    |
| `tiers/*.yml`           | Individual tier definitions (schema v2)         |
| `messages.yml`          | Custom message strings                       |
| `menus.yml`             | GUI layout and styling                       |

## Tier Bootstrap Warning

On first startup, if the `tiers/` directory does not exist, FlameForge will copy seven default tier files (tier1.yml through tier7.yml) into that directory. If you are upgrading and already have a `tiers/` directory, existing files will not be overwritten.

**Deleting a tier file** removes that tier from the forge. Deleting all tier files does not disable the plugin; players will see an empty tier selection menu.

## Hooks

FlameForge provides command-based hooks for reward integration and economy:

- **Vault**: Money costs via `CostMode.MONEY_ONLY` or `CostMode.XP_AND_MONEY`.
- **PlaceholderAPI**: Expansion support for scoreboard and chat integration (separate plugin required).

## Safety

- **Anti-dupe**: Transaction journal prevents item duplication on disconnect. Pending deliveries are queued and delivered on player join.
- **Session validation**: Items are held in custody only during animation. Disconnecting during animation returns items and refunds costs.
- **Atomic operations**: Tier selection, cost charging, and outcome execution occur within a session state machine to prevent race conditions.

## Documentation

- [PROJECT-SPEC](docs/PROJECT-SPEC.md) — Acceptance contract
- [ARCHITECTURE](docs/ARCHITECTURE.md) — Object model and threading
- [CONFIGURATION](docs/CONFIGURATION.md) — File schemas and examples
- [COMMANDS](docs/COMMANDS-AND-PERMISSIONS.md) — Permission nodes and command reference
- [OUTCOMES](docs/OUTCOMES-AND-HOOKS.md) — Outcome types and hook system
- [ADMIN-GUIDE](docs/ADMIN-GUIDE.md) — Setup, validation, backup, troubleshooting
- [FEATURE-EVALUATION](docs/FEATURE-EVALUATION.md) — Feature selection rationale
