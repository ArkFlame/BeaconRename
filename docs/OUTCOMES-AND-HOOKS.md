# FlameForge Outcomes and Hooks

## Outcome Categories

All outcomes fall into three categories based on their effect on the input item:

| Category | Description |
|----------|-------------|
| **SUCCESS** | Item is returned in modified form |
| **BREAK** | Input item is destroyed |
| **CURSE** | Negative effect applied to item |

## Outcome Types

### SUCCESS — Modify Input

The input item is mutated and returned.

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

**Effect:** The input item has enchantments, attributes, name, lore, or material changed per the mutation spec, then returned. If `same_material` is true, the material is preserved.

#### Mutation Spec Fields

| Field          | Type             | Description                                      |
|----------------|------------------|--------------------------------------------------|
| `same_material` | Boolean          | If true, material is preserved (default: false) |
| `material`     | Material key     | Changes the item's material type                 |
| `name`         | MiniMessage      | Sets the display name                            |
| `amount`       | Integer          | Sets stack size (default: 1)                    |
| `enchants`     | Map              | Enchantments to add/remove                       |
| `attributes`   | Map              | Attribute modifiers to add                       |

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

### BREAK

The input item is consumed and no output is returned.

```yaml
break_item:
  type: BREAK
  weight: 10
```

**Effect:** Input slot items are removed. No item is returned.

### CURSE — Apply Curse

Negative effects applied to the input item.

```yaml
curse_void:
  type: CURSE
  weight: 5
  curse:
    type: VOID
    description: "<red>Void corruption"
```

**CURSE variants:**

| Type    | Effect |
|---------|--------|
| `VOID`  | Marks item with void curse |
| `DECAY` | Reduces item durability on each reforge |
| `DRAIN` | Reduces item stats |

### Same-Material Variants

When `same_material: true` is set, the item's material is preserved through the mutation. This allows enchantment upgrades without changing the base item type.

```yaml
preserve_diamond:
  type: MODIFY_INPUT
  weight: 30
  mutation:
    same_material: true
    enchants:
      sharpness_boost:
        name: DAMAGE_ALL
        min-level: 1
```

## Powers

Powers are named stat bundles applied via outcomes. A power is a collection of enchantments, attributes, and display modifications.

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

outcomes:
  apply_blazing:
    type: MODIFY_INPUT
    weight: 25
    power: blazing
```

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

### Command-Based Reward Hooks

External plugins can register custom commands via the `COMMANDS` outcome type for reward integration.

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
