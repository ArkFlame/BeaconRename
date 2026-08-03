# FlameForge Commands and Permissions

## Commands Reference

All commands are subcommands of `/flameforge` (aliases: `forge`, `ff`).

### Help

```
/flameforge help [path]
```
Displays hierarchical help menu without pages. The optional `path` argument filters to a specific command group (e.g., `flameforge help station`). Each entry supports click-to-insert — clicking a command suggestion inserts it into the chat input.

- Default permission: `flameforge.command.help` (all players)

### Open Forge

```
/flameforge open [player]
```
Opens the forge menu. If `player` is specified and sender has `flameforge.command.open.others`, opens the menu for that player.

- Default permission: `flameforge.command.open` (all players)

### Reload

```
/flameforge reload
```
Reloads all configuration files and rebuilds internal snapshots. Async operation.

- Default permission: `flameforge.command.reload` (op)

### Validate

```
/flameforge validate
```
Runs configuration validation without reloading. Reports errors and warnings.

- Default permission: `flameforge.command.validate` (op)

### List Tiers

```
/flameforge tiers [page]
```
Lists all configured tiers with their level values.

- Default permission: `flameforge.command.tiers` (op)

### Tier Info

```
/flameforge tier info <tier-id>
```
Shows detailed information about a specific tier: level, requirements, animation durations, cost mode, cooldown, and outcomes.

- Default permission: `flameforge.command.tier.info` (op)

### Preview

```
/flameforge preview <tier-id> [material]
```
Shows a preview of the first outcome's mutation result for the held item (or specified material). Does not consume items or charge costs.

- Default permission: `flameforge.command.preview` (op)

### History

```
/flameforge history [player]
```
Shows reforge history for the player. Without arguments, shows sender's own history.

- Default permission: `flameforge.command.history` (all players)
- Other players: `flameforge.command.history.others` (op)

### Station Management

```
/flameforge station add [id] [profile]
```
Registers any non-air block the player is looking at as a forge. Player must be within 6 blocks of the target. ID and profile are optional; profile defaults to `default`.

- Default permission: `flameforge.command.station.add` (op)

```
/flameforge station remove <id>
```
Removes a registered station by its ID.

- Default permission: `flameforge.command.station.remove` (op)

```
/flameforge station list [page]
```
Lists all registered stations with IDs, coordinates, and profiles.

- Default permission: `flameforge.command.station.list` (op)

```
/flameforge station info <id>
```
Shows detailed information about a station: world, coordinates, profile, max tier.

- Default permission: `flameforge.command.station.info` (op)

```
/flameforge station teleport <id>
```
Teleports the player to a station's location. On Folia, uses entity scheduler.

- Default permission: `flameforge.command.station.teleport` (op)

### Tier Setup

```
/flameforge setup tier create <id> <level>
```
Creates a new empty tier file in `tiers/` with the specified ID and level. The file is created with schema version 2 but contains no outcomes.

- Default permission: `flameforge.command.setup.tier` (op)

```
/flameforge setup tier clone <source-id> <new-id> <level>
```
Clones an existing tier file to a new ID with a new level.

- Default permission: `flameforge.command.setup.tier` (op)

## Permission Nodes

### Base Permissions

| Permission                    | Description                              | Default |
|-------------------------------|------------------------------------------|---------|
| `flameforge.use`              | Basic plugin access                      | true    |
| `flameforge.admin`            | All command permissions, including `flameforge.use` | op      |

### Command Permissions

| Permission                           | Description                              | Default |
|--------------------------------------|------------------------------------------|---------|
| `flameforge.command.help`           | Help menu                                | true    |
| `flameforge.command.open`            | Open forge menu                          | true    |
| `flameforge.command.open.others`     | Open menu for other players              | op      |
| `flameforge.command.reload`         | Reload configuration                     | op      |
| `flameforge.command.validate`        | Validate configuration                   | op      |
| `flameforge.command.tiers`           | List tiers                               | op      |
| `flameforge.command.tier.info`       | View tier information                    | op      |
| `flameforge.command.preview`         | Preview outcomes                         | op      |
| `flameforge.command.history`          | View own history                         | true    |
| `flameforge.command.history.others`  | View other players' history              | op      |
| `flameforge.command.station.add`     | Register stations                        | op      |
| `flameforge.command.station.remove`  | Remove stations                          | op      |
| `flameforge.command.station.list`    | List stations                            | op      |
| `flameforge.command.station.info`    | View station information                 | op      |
| `flameforge.command.station.teleport` | Teleport to stations                    | op      |
| `flameforge.command.setup.tier`      | Create/clone tiers                       | op      |

### Bypass Permissions

| Permission                  | Description                              | Default |
|-----------------------------|------------------------------------------|---------|
| `flameforge.bypass.cost`    | Skip cost requirements                   | op      |
| `flameforge.bypass.cooldown`| Skip cooldown periods                    | op      |

## Tab Completion

## Click Interception

Help entries, suggestions, and hover text are rendered via Adventure MiniMessage. Clicking a help entry inserts the full command label and suggestion into the player's chat input, allowing players to quickly type commands without copying manually.

## Tab Completion

Tab completion is supported for:
- Subcommand names
- Online player names (for `open` and `history`)
- Tier IDs (for `tier info`, `preview`, `setup tier clone`)
- Station IDs (for `station remove`, `station info`, `station teleport`)
- Material names and aliases (for `preview`)
- Profile names (for `station add`)
