# FlameForge Configuration Reference

## File Overview

| File                  | Auto-generated | Purpose                                      |
|-----------------------|----------------|----------------------------------------------|
| `config.yml`          | Yes (first run)| Root settings, announcements                 |
| `stations.yml`        | No             | Registered station data (managed by plugin)  |
| `station-profiles.yml`| No             | Forge station profiles                       |
| `tiers/*.yml`         | On bootstrap   | Tier definitions (schema v2)                   |
| `messages.yml`        | No             | Custom message strings                       |
| `menus.yml`           | No             | GUI layout and styling                       |

## config.yml

Root configuration file. Located at `plugins/FlameForge/config.yml`.

### Schema

```yaml
# Schema version — do not modify
schema-version: 2

# Plugin enabled state
enabled: true

# Station behavior mode
# REGISTERED_ONLY: only registered forges work
# ANY_BLOCK: any non-air block is a forge station
station-mode: REGISTERED_ONLY

# Audit log settings
audit-queue-capacity: 1024
audit:
  enabled: true
  folder: audit
  max-file-age-days: 30

# Chance display precision
chance-decimals: 4
chance-display-decimals: 1

# Enchantments blocked from forging
unsafe-enchants:
  - CURSE_OF_VOIDING
  - CURSE_OF_TERRIBLE_DEATH

# Material group aliases for matching
item-groups:
  <group-name>:
    materials:
      - <material>
      - <material>

# Announcement settings
announcements:
  global:
    enabled: true
    radius: 0  # 0 = worldwide
    title:
      success: "<MiniMessage>"
      fail: "<MiniMessage>"
    subtitle:
      success:
        - "<MiniMessage>"
      fail:
        - "<MiniMessage>"
  station:
    enabled: true
    radius: 16
    title:
      success: "<MiniMessage>"
      fail: "<MiniMessage>"
    subtitle:
      success:
        - "<MiniMessage>"
      fail:
        - "<MiniMessage>"

# Animation and menu profile defaults
animation-profile: default
menu-profile: default

# Cost display formatting
cost-colors:
  xp: "<light_purple>"
  money: "<green>"
  xp-label: "<light_purple>XP Cost:"
  money-label: "<green>Money Cost:"

cost-display:
  xp-format: "{value} XP"
  money-format: "${value}"
  show-zero-xp: false
  show-zero-money: false

# Hologram settings
holograms:
  enabled: true
  provider-order:
    - FancyHolograms  # v2+
    - DecentHolograms
  offset-y: 1.75
  transparent-background: true
  lines:
    - "<gradient:#ff5f00:#ffd166><bold>FlameForge</bold></gradient>"
    - "<gray>%forge_id%"

# Tier migration settings
tier-migration:
  auto-upgrade: true
  backup-original: true
```

### Config Overlay Behavior

FlameForge uses a bundled-default overlay strategy for `config.yml`. The bundled `config.yml` inside the JAR is loaded first as defaults. The operator's `config.yml` is then overlaid, with operator values taking precedence for leaf keys. This means:

- An operator `config.yml` without a `holograms` section inherits the full bundled defaults for holograms.
- Only explicitly set leaf keys are overridden; absent sections are filled from bundled defaults.
- This applies to all root-level leaf keys including holograms settings.

### Hologram `enabled: false`

Setting `holograms.enabled: false` disables hologram creation entirely. The plugin logs `disabled by configuration` and uses a no-op provider. Existing holograms from prior sessions are not automatically removed unless the plugin is reloaded or restarted.

### Hologram Provider Order

The `provider-order` list specifies the priority for hologram library selection. Default order:

```yaml
holograms:
  provider-order:
    - FancyHolograms
    - DecentHolograms
```

Selection iterates through the list in order. Each entry must match a known provider name (`FancyHolograms` or `DecentHolograms`) and the corresponding plugin must be enabled. The first available provider is used. If the list is empty or no provider is available, no holograms are created.

### Supported Hologram Providers

FlameForge supports two hologram libraries via soft-depend:

- **FancyHolograms** (v2+) — preferred; uses MiniMessage text formatting
- **DecentHolograms** — fallback; uses legacy color code formatting

If `holograms.enabled` is `true`, the plugin queries the server for available hologram providers in the order specified by `provider-order` and creates floating text displays at forge stations.

### Tier Migration Settings

```yaml
tier-migration:
  auto-upgrade: true      # automatically upgrade schema v1 tiers to v2
  backup-original: true   # backup original files before migration
```

When `auto-upgrade` is enabled, schema v1 tier files are automatically converted to schema v2 format on load.

### Material Candidate Syntax

Materials in configuration accept two forms:

| Syntax | Example | Behavior |
|--------|---------|----------|
| `MATERIAL_NAME` | `DIAMOND_SWORD` | Resolves to the modern Bukkit material |
| `MATERIAL_NAME:legacyData` | `STAINED_GLASS_PANE:15` | Resolves material with legacy data value (for version-specific variants) |

The colon syntax is used for legacy data values. Example aliases in `MaterialResolver`:

```yaml
black_stained_glass_pane:
  - BLACK_STAINED_GLASS_PANE
  - STAINED_GLASS_PANE:15
```

When multiple candidates are specified (e.g., menu filler items), FlameForge uses the first valid material from the candidate list.

### Menu Icon Material Resolution

Menu icons use fallback material chains via `MaterialResolver.itemOrThrow()`. The first valid material in the candidate list is used. If no candidate resolves, an exception is thrown and the menu fails to open.

Example filler resolution:
```java
MATERIAL_RESOLVER.itemOrThrow(1, "GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:7", "GLASS_PANE");
```

### Example: Announcement Configuration

```yaml
announcements:
  global:
    enabled: true
    radius: 0
    title:
      success: "<gold><bold>FORGE SUCCESS!"
      fail: "<red><bold>FORGE FAILED"
    subtitle:
      success:
        - "<white>%player_name% <green>received a powerful item!"
        - "<gray>%item_name%"
      fail:
        - "<white>%player_name% <red>lost their item"
```

## stations.yml

Station registry. Managed by the plugin; manual editing is not recommended.

Any non-air block can be registered. In `REGISTERED_ONLY` mode, only registered forge blocks accept interaction.

### Schema

```yaml
<coordinate-key>:
  id: <station-id>
  world: <world-name>
  x: <double>
  y: <double>
  z: <double>
  profile: <profile-id>
```

### Example

```yaml
world_100_64_-200:
  id: main_forge
  world: world
  x: 100.0
  y: 64.0
  z: -200.0
  profile: default
```

## Tier Files (tiers/*.yml)

Each tier is defined in its own YAML file under `plugins/FlameForge/tiers/`.

### Schema v2

```yaml
# Schema version — must be 2
schema-version: 2

# Unique tier identifier
id: <string>

# Tier level for automatic progression (replaces priority)
level: <integer>

# Tier requirements for input items
requirements:
  items:
    - material: <material>
      required: true|false
  strict-match: false

display:
  name: "<MiniMessage>"
  lore:
    - "<MiniMessage>"
  material: <material>
  custom-model-data: <integer>

# Cost configuration
cost:
  mode: XP_ONLY|XP_AND_MONEY|XP_OR_MONEY|MONEY_ONLY
  xp: <decimal>
  money: <decimal>

# Cooldown in seconds (0 = no cooldown)
cooldown-seconds: 0

# Animation durations (in ticks)
animation:
  success-duration: 40
  fail-duration: 20
  success-steps:
    <step-id>:
      delay: 0
      type: <step-type>
      data: <string>
  fail-steps:
    <step-id>:
      delay: 0
      type: <step-type>
      data: <string>

# Powers definitions
powers:
  <power-id>:
    enchants:
      <enchant-id>:
        name: <enchantment>
        min-level: <integer>
        max-level: <integer>
    attributes:
      <attr-id>:
        name: <attribute>
        min-value: <double>
        max-value: <double>
        operation: ADD_NUMBER|ADD_SCALAR_1|ADD_SCALAR_2

# Outcomes
outcomes:
  <outcome-id>:
    type: MODIFY_INPUT|BREAK|CURSE
    category: SUCCESS|BREAK|CURSE
    weight: <decimal>
    power: <power-id>
    mutation:
      same-material: true|false
      material: <material>
      name: "<MiniMessage>"
      amount: <integer>
      enchants:
        <enchant-id>:
          name: <enchantment>
          min-level: <integer>
          max-level: <integer>
      attributes:
        <attr-id>:
          name: <attribute>
          min-value: <double>
          max-value: <double>
          operation: ADD_NUMBER|ADD_SCALAR_1|ADD_SCALAR_2
    curse:
      type: VOID|DECAY|DRAIN
      description: "<MiniMessage>"
```

### Outcome Types

| Type             | Category | Description                                           |
|------------------|----------|-------------------------------------------------------|
| `MODIFY_INPUT`   | SUCCESS  | Original item mutated and returned                    |
| `BREAK`          | BREAK    | Item is destroyed                                     |
| `CURSE`          | CURSE    | Negative effect applied                               |

### CURSE Variants

| Variant | Effect |
|---------|--------|
| `VOID`  | Marks item with void curse |
| `DECAY` | Reduces item durability on each reforge |
| `DRAIN` | Reduces item stats |

### Example: Simple Tier

```yaml
schema-version: 2
id: common
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
      description: "<red>Stats drained"
```

### Example: Tier with Powers

```yaml
schema-version: 2
id: legendary
level: 100
requirements:
  items:
    - material: DIAMOND_SWORD
      required: true
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
display:
  name: "<gold>Legendary Forge"
  lore:
    - "<yellow>Chance at legendary rewards"
  material: NETHER_STAR
cost:
  mode: XP_AND_MONEY
  xp: 500
  money: 1000
cooldown-seconds: 3600
outcomes:
  legendary_blazing:
    type: MODIFY_INPUT
    category: SUCCESS
    weight: 10
    power: blazing
  break_item:
    type: BREAK
    category: BREAK
    weight: 90
```

## Station Profiles (stations.yml section)

Station profiles are defined within `config.yml` under a `stations` top-level key (loaded by `loadStations` in `ConfigService`).

### Schema

```yaml
stations:
  <profile-id>:
    station-id: <string>
    max-tier: <integer>  # -1 = no limit
    permissions:
      - <permission-node>
    menu: <menu-profile-id>
    animation: <animation-profile-id>
    announcement-radius: <integer>
```

### Example

```yaml
stations:
  default:
    max-tier: -1
    permissions: []
  donor_only:
    max-tier: 100
    permissions:
      - flameforge.tier.donor
    menu: donor_menu
  low_tier:
    max-tier: 10
    permissions: []
```

## messages.yml

Custom messages. Keys are arbitrary identifiers.

### Example

```yaml
forge_success:
  text: "<gold>Forge complete!</gold>"
  whisper: false
item_break:
  text: "<red>Your item was destroyed!</red>"
  whisper: true
```

## menus.yml

GUI styling configuration.

### Example

```yaml
default:
  title: "FlameForge"
  fill:
    enabled: true
    material: BLACK_STAINED_GLASS_PANE
```
