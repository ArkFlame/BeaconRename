# FlameForge Outcomes and Hooks

## Outcome Types

Outcomes define what happens when a forge tier is executed. Each tier has one or more outcomes with assigned weights that determine probability via weighted random selection.

### BREAK

The input item is consumed and no output is returned.

```yaml
break_item:
  type: BREAK
  weight: 10
```

**Effect:** Input slot items are removed. No item is returned to the player unless a ward converts this to `RETURN_UNCHANGED`.

### RETURN_UNCHANGED

The input item is returned in its original state.

```yaml
no_change:
  type: RETURN_UNCHANGED
  weight: 25
```

**Effect:** Input items are cloned and returned to the player's inventory.

### MODIFY_INPUT

The input item is mutated according to the mutation spec and returned.

```yaml
upgrade_sharpness:
  type: MODIFY_INPUT
  weight: 40
  mutation:
    enchants:
      sharpness_boost:
        name: DAMAGE_ALL
        min-level: 1
```

**Effect:** The input item has its enchantments, attributes, name, lore, or material changed per the mutation spec, then returned.

#### Mutation Spec Fields

| Field       | Type             | Description                                   |
|-------------|------------------|-----------------------------------------------|
| `material`  | Material key     | Changes the item's material type              |
| `name`      | MiniMessage      | Sets the display name                         |
| `amount`    | Integer          | Sets stack size (default: 1)                  |
| `enchants`  | Map              | Enchantments to add/remove                    |
| `attributes`| Map              | Attribute modifiers to add                    |

#### Enchantment Spec

```yaml
enchants:
  <key>:
    name: <enchantment-id>     # e.g., DAMAGE_ALL, PROTECTION_ENVIRONMENTAL
    min-level: <integer>       # level to set
    max-level: <integer>       # max allowed level (for clamping)
```

#### Attribute Spec

```yaml
attributes:
  <key>:
    name: <attribute-id>       # e.g., GENERIC_ATTACK_DAMAGE
    min-value: <double>
    max-value: <double>
    operation: ADD_NUMBER|ADD_SCALAR_1|ADD_SCALAR_2
```

### CREATE_ITEM

A new item is created from the mutation spec and delivered to the player. The original input is consumed.

```yaml
spawn_legendary:
  type: CREATE_ITEM
  weight: 5
  mutation:
    material: DIAMOND_SWORD
    name: "<gold>Legendary Blade"
    enchants:
      sharpness_5:
        name: DAMAGE_ALL
        min-level: 5
```

**Effect:** A new ItemStack is created and delivered. If the player's inventory is full, the item is dropped at their location.

### COMMANDS

Console commands are dispatched. Placeholders `%player_name%`, `%player%`, and `%player_uuid%` are substituted.

```yaml
reward_commands:
  type: COMMANDS
  weight: 15
  commands:
    - "give %player_name% diamond 1"
    - "eco give %player_name% 100"
```

**Effect:** Commands are dispatched via `Bukkit.dispatchCommand()` on the console sender. Commands are scheduled asynchronously and logged to the audit trail.

## Hook System

### Vault Economy

FlameForge integrates with Vault for economy-based costs when Vault and a compatible economy plugin are installed.

**Detection:** Soft-depend on Vault. If Vault is not present, money costs are unavailable.

**Supported cost modes:**
- `MONEY_ONLY` — deducts from player's economy balance
- `XP_AND_MONEY` — deducts both XP levels and money
- `XP_OR_MONEY` — player chooses which to use

**Cost service interface:**
```java
public interface EconomyService {
    boolean available();
    BigDecimal balance(OfflinePlayer player);
    boolean withdraw(OfflinePlayer player, BigDecimal amount);
    boolean deposit(OfflinePlayer player, BigDecimal amount);
    String format(BigDecimal amount);
}
```

**NoEconomyService** is the fallback when Vault is absent; `available()` returns false and all withdraw calls fail.

### SMPWeapons Integration

FlameForge detects SMPWeapons via `PluginConditionService.isPluginEnabled("SMPWeapons")`. This information is displayed in the server startup log and can be used by external plugins to coordinate.

SMPWeapons is a soft dependency. FlameForge does not directly call SMPWeapons APIs.

### Command-Based Reward Hooks

The `COMMANDS` outcome type serves as the generic reward integration point. External plugins can register custom commands that grant rewards (e.g., from other plugins' reward systems).

**Example: Integration with a custom reward plugin**

```yaml
outcomes:
  mmo_reward:
    type: COMMANDS
    weight: 10
    commands:
      - "mmorewards give %player_name% common_box"
      - "playsound %player_name% entity.player.levelup master"
```

**Placeholder substitution:**
- `%player_name%` — player display name
- `%player%` — same as `%player_name%`
- `%player_uuid%` — player's UUID string

### Plugin Condition Hooks

The `PluginConditionService` provides runtime detection of server plugins:

```java
public boolean isPluginEnabled(String pluginName);
public boolean isVaultEnabled();
```

This allows FlameForge to adapt behavior based on available plugins without hard dependencies.

### Generic Command Hook (SMPWeapons Compatible)

For direct SMPWeapons API integration (if the SMPWeapons hook is not available), the generic `COMMANDS` outcome type dispatches:

```yaml
outcomes:
  smpweapons_grant:
    type: COMMANDS
    weight: 10
    commands:
      - "smpweapons give %player_name% rare_sword"
```

This approach works with or without SMPWeapons present.

## Catalyst and Ward Hooks

### Catalyst Processing

Catalysts are processed before outcome selection. The `catalyst` section in `config.yml` defines:

- `chance-modifier`: A decimal bonus applied to the total weight table
- `protected-outcomes`: Outcome IDs that the catalyst blocks

**Flow:**
1. Player places catalyst in designated slot
2. On forge confirm, catalyst is validated against tier catalyst requirements
3. If valid, `chance-modifier` adjusts the chance table
4. If outcome selected is in `protected-outcomes`, re-roll
5. If `consume: true`, catalyst item is consumed

### Ward Processing

Wards are evaluated after outcome selection. The `ward` section in `config.yml` defines:

- `protected-outcomes`: Outcome IDs the ward blocks
- `convert_to_unchanged`: Outcome types (e.g., `BREAK`) converted to `RETURN_UNCHANGED`
- `protect_all`: Boolean to protect against all BREAK outcomes

**Flow:**
1. Outcome is selected from chance table
2. Ward is checked against `protected-outcomes`
3. If matched and `convert_to_unchanged` contains the outcome type, outcome is converted to `RETURN_UNCHANGED`
4. Ward item is consumed

## Outcome Filtering

The `OutcomeSelector` class supports pluggable filters:

```java
public interface CatalystFilter {
    boolean test(OutcomeDefinition outcome);
}

public interface WardFilter {
    boolean test(OutcomeDefinition outcome);
}
```

These filters are applied before weight calculation, allowing dynamic outcome exclusion based on item properties, player state, or server conditions.
