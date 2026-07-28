# FlameForge Configuration Reference

## File Overview

| File                  | Auto-generated | Purpose                                      |
|-----------------------|----------------|----------------------------------------------|
| `config.yml`          | Yes (first run)| Root settings, catalysts, wards, announcements |
| `stations.yml`        | No             | Registered station data (managed by plugin)  |
| `tiers/*.yml`         | On bootstrap   | Tier definitions                              |
| `messages.yml`        | No             | Custom message strings                       |
| `menus.yml`           | No             | GUI layout and styling                       |

## config.yml

Root configuration file. Located at `plugins/FlameForge/config.yml`.

### Schema

```yaml
# Schema version — do not modify
schema-version: 1

# Plugin enabled state
enabled: true

# Station behavior mode
# REGISTERED_ONLY: only registered beacons work
# ANY_BEACON: any beacon block is a forge station
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

# Catalyst definitions
catalysts:
  <catalyst-id>:
    enabled: true
    material: <material>
    name: "<MiniMessage>"
    lore:
      - "<MiniMessage>"
    chance-modifier: <decimal>  # bonus weight multiplier
    consume: true|false
    protected-outcomes:
      - <outcome-id>

# Ward (break-protection) definitions
wards:
  <ward-id>:
    enabled: true
    material: <material>
    name: "<MiniMessage>"
    lore:
      - "<MiniMessage>"
    protected-outcomes:
      - <outcome-id>
    convert_to_unchanged:
      - BREAK
    protect_all: false

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

# Pity system defaults (can be overridden per tier)
pity:
  enabled: true
  default-threshold: 10
  default-bonus-weight: 2.0

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
```

### Example: Catalyst Configuration

```yaml
catalysts:
  lucky_dust:
    enabled: true
    material: GLOWSTONE_DUST
    name: "<reset><white>Lucky Dust"
    lore:
      - "<gray>Optional catalyst"
      - "<gray>Place to modify outcomes"
    chance-modifier: 0.1
    consume: true
    protected-outcomes:
      - legendary_ward
      - epic_ward
```

### Example: Ward Configuration

```yaml
wards:
  safety_rune:
    enabled: true
    material: PRISMARINE_SHARD
    name: "<reset><aqua>Safety Rune"
    lore:
      - "<gray>Optional ward"
      - "<gray>Place to protect your item"
    protected-outcomes:
      - legendary_ward
      - epic_ward
    convert_to_unchanged:
      - BREAK
    protect_all: false
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

### Schema

```yaml
# Schema version — must be 1
schema-version: 1

# Unique tier identifier
id: <string>

# Priority for sorting (higher = appears first in menu)
priority: <integer>

# Whether this tier is enabled
enabled: true

# Permission required to use this tier (empty = no permission)
permission: ""

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

# Pity system
pity:
  enabled: true|false
  threshold: <integer>
  bonus-weight: <decimal>

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

# Outcomes
outcomes:
  <outcome-id>:
    type: BREAK|RETURN_UNCHANGED|MODIFY_INPUT|CREATE_ITEM|COMMANDS
    weight: <decimal>
    mutation:
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
    commands:
      - "<console command>"
```

### Outcome Types

| Type             | Description                                           | Requires mutation |
|------------------|-------------------------------------------------------|-------------------|
| `BREAK`          | Item is destroyed                                     | No                |
| `RETURN_UNCHANGED` | Original item returned                             | No                |
| `MODIFY_INPUT`   | Original item mutated and returned                    | Yes               |
| `CREATE_ITEM`    | New item created and delivered                        | Yes               |
| `COMMANDS`       | Console commands dispatched                           | No                |

### Example: Simple Tier with Break and Modify Outcomes

```yaml
schema-version: 1
id: common
priority: 10
enabled: true
permission: ""
display:
  name: "<white>Common Forge"
  lore:
    - "<gray>Basic reforge"
  material: IRON_INGOT
cost:
  mode: XP_ONLY
  xp: 10
  money: 0
cooldown-seconds: 60
pity:
  enabled: true
  threshold: 5
  bonus-weight: 1.5
animation:
  success-duration: 30
  fail-duration: 15
outcomes:
  return_unchanged:
    type: RETURN_UNCHANGED
    weight: 50
  add_sharpness:
    type: MODIFY_INPUT
    weight: 45
    mutation:
      enchants:
        sharpness_1:
          name: DAMAGE_ALL
          min-level: 1
          max-level: 1
  break_item:
    type: BREAK
    weight: 5
```

### Example: Tier with CREATE_ITEM Outcome

```yaml
schema-version: 1
id: legendary
priority: 100
enabled: true
permission: ""
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
pity:
  enabled: true
  threshold: 10
  bonus-weight: 2.0
animation:
  success-duration: 80
  fail-duration: 40
outcomes:
  legendary_sword:
    type: CREATE_ITEM
    weight: 1
    mutation:
      material: DIAMOND_SWORD
      name: "<gold>Legendary Blade"
  epic_sword:
    type: CREATE_ITEM
    weight: 4
    mutation:
      material: DIAMOND_SWORD
      name: "<aqua>Epic Blade"
  break_item:
    type: BREAK
    weight: 95
```

### Example: Tier with Commands Outcome

```yaml
schema-version: 1
id: reward_tier
priority: 50
enabled: true
display:
  name: "<green>Reward Tier"
  material: EMERALD
cost:
  mode: XP_ONLY
  xp: 100
cooldown-seconds: 0
outcomes:
  give_reward:
    type: COMMANDS
    weight: 100
    commands:
      - "give %player_name% diamond 1"
      - "eco give %player_name% 500"
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
