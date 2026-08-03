# FlameForge Architecture

## Object Model

### Core Domain Objects

```
FlameForgePlugin
├── ConfigService
│   └── ConfigSnapshot
│       ├── tiers: List<TierDefinition>
│       ├── stationProfiles: Map<String, Map<String, Object>>
│       ├── validationReport: ValidationReport
│       └── rootSettings: Map<String, Object>
├── TierRepository
│   └── Map<String, TierDefinition>
│   └── Map<String, TierParser.TierExtra>
├── ForgeStationService
│   ├── StationRepository
│   │   └── Map<String, StationData>
│   └── StationProfile (immutable value)
├── ForgeMenuService
│   └── Map<UUID, ForgeMenuContext>
├── ForgeSessionService
│   └── Map<String, ForgeSession>
├── ForgeService
│   ├── CostService
│   │   └── EconomyService (Vault or NoOp)
│   ├── OutcomeExecutor
│   │   └── AuditLogService
│   ├── DeliveryService
│   │   └── PendingDeliveryRepository
│   └── ForgeAnimationService
├── PlayerStateRepository
│   └── ConcurrentHashMap<UUID, PlayerState>
└── AuditLogService
    └── BlockingQueue<String>
```

### Key Classes

| Class                    | Role                                                      |
|--------------------------|-----------------------------------------------------------|
| `FlameForgePlugin`       | Plugin lifecycle, service wiring                          |
| `ConfigService`          | Configuration reload, snapshot management                 |
| `TierRepository`         | Tier file loading, CRUD operations                        |
| `TierDefinition`         | Immutable tier: id, level, requirements, outcomes         |
| `TierParser`             | YAML parsing into TierDefinition + TierExtra              |
| `StationRepository`      | Station persistence (stations.yml)                        |
| `ForgeStationService`    | Station resolution, permission checks, teleport            |
| `ForgeSessionService`    | Player session state machine                              |
| `ForgeSession`           | OPEN → PROCESSING → SETTLING → CLOSED                   |
| `ForgeContext`           | Immutable snapshot of forge parameters at confirm time    |
| `ForgeTransaction`       | Atomic record: custody, charge, chance table, outcome     |
| `OutcomeExecutor`        | Dispatches outcome effects: SUCCESS, BREAK, CURSE         |
| `ItemMutationService`    | Applies enchantments, attributes, name, lore to ItemStack  |
| `ItemMatcher`            | Matches ItemStack against ItemMatcherSpec                |
| `PlayerStateRepository`  | YAML-based player state persistence                       |
| `AuditLogService`        | Async JSONL writer with bounded queue                     |
| `PendingDeliveryRepository` | Queues items/commands for offline/delayed delivery      |

## Outcome Types

| Enum Value       | Category   | Behavior                                                       |
|------------------|------------|----------------------------------------------------------------|
| `MODIFY_INPUT`   | SUCCESS    | Input item mutated per ItemMutationSpec, returned              |
| `BREAK`          | BREAK      | Input item is consumed, no output                              |
| `CURSE`          | CURSE      | Negative effect applied to item                                |

**Removed outcome types:** `CREATE_ITEM`, `COMMANDS`, `RETURN_UNCHANGED`

## Menu Primitives

The 27-slot menu uses these primitives:

| Primitive       | Description |
|-----------------|-------------|
| `INPUT_SLOT`    | Single center slot for the item to reforge |
| `TIER_LIST`     | Right-side tier buttons with automatic next-tier |
| `CONFIRM_BUTTON`| Bottom-center execution trigger |
| `NAVIGATION`    | Bottom corners for paging and close |

**Removed primitives:** `CATALYST_SLOT`, `WARD_SLOT`, `PITY_COUNTER`

## Identity v2

`ItemIdentityService` v2 writes and reads a PersistentDataContainer (PDC) tag on items to track:
- Forge origin station
- Reforge count
- Material group membership
- Same-material preservation flag

This enables:
- PDC-based item matching in tier requirements
- Reforge count bounds in item matching
- Material group aliases
- Same-material variant detection

## Powers System

Powers are named stat bundles:

```java
public class Power {
    String name;
    Map<String, EnchantmentSpec> enchants;
    Map<String, AttributeSpec> attributes;
    MiniMessage displayName;
    List<MiniMessage> description;
}
```

Powers are referenced by outcomes and applied via `ItemMutationService.applyPower()`.

## Tier Model (Schema v2)

```java
public class TierDefinition {
    String id;
    int level;                    // replaces priority
    TierRequirements requirements;
    TierDisplay display;
    CostSpec cost;
    int cooldownSeconds;
    List<OutcomeDefinition> outcomes;
}
```

### TierRequirements

```java
public class TierRequirements {
    List<ItemMatcher> items;     // required input items
    boolean strictMatch;          // all items must be present
}
```

## Forge Block Registration

Station registration accepts any non-air block. Material type does not identify a forge; the station registry does. In `REGISTERED_ONLY` mode, interaction opens the menu only for a registered forge block.

## Threading Model

### Scheduler Bridge

The `SchedulerBridge` abstraction provides server-appropriate scheduling:

| Server Type | Implementation      | Notes                                    |
|-------------|---------------------|------------------------------------------|
| Spigot      | `BukkitSchedulerBridge` | Schedules on main server thread      |
| Paper/Folia | `BukkitSchedulerBridge` | Same, Paper supports these APIs     |
| Folia       | `FoliaSchedulerBridge`   | Uses entity/region schedulers      |

### Thread Safety Rules

1. **Config reload**: Runs async, installs new snapshot atomically via `AtomicReference`.
2. **Player state**: `ConcurrentHashMap` for in-memory cache; updates use CAS via `atomicReplace`.
3. **Audit log**: Dedicated daemon writer thread; queue is bounded `LinkedBlockingQueue`.
4. **Station data**: `ConcurrentHashMap` with async save; reads are lock-free snapshots.
5. **Forge sessions**: Each session has an intrinsic lock; external callers must not hold class-level locks when calling session transitions.
6. **Menu state**: Single-threaded per player (Bukkit task); no concurrent access.

### Cross-Thread Discipline

- All Bukkit API calls (inventory, player, world) happen on the main thread via scheduler.
- Async operations are used for: file I/O, config reload, audit write, command dispatch.
- Folia entity schedulers are used for player teleport and chunk-aware operations.

## State Machine

### Forge Session States

```
┌──────┐  open()   ┌───────────┐  confirm()   ┌────────────┐  animation completes  ┌──────────┐
│ NONE │ ────────▶ │   OPEN    │ ───────────▶ │ PROCESSING │ ─────────────────────▶ │ SETTLING │
└──────┘           └───────────┘              └────────────┘                       └──────────┘
                                           │                   │
                                           │                   │ animation null
                                           │                   ▼
                                           │              ┌────────────┐
                                           └─────────────▶ │   CLOSED   │
                                                           └────────────┘
```

- **OPEN**: Player may modify inputs, tier selection. Custody not yet taken.
- **PROCESSING**: Inputs consumed, cost charged, outcome selected, animation playing.
- **SETTLING**: Animation complete, outcome executing, delivery queued.
- **CLOSED**: Terminal state. Resolution recorded.

### Session Transitions

- `session.atomicOpenToProcessing(ctx, tx)`: CAS from OPEN to PROCESSING.
- On failure: refund, return custody.
- On player quit in OPEN: refund, return custody, close.
- On player quit in PROCESSING: queue pending delivery for outcome execution on join.

## Data Flow

```
Player clicks registered forge block
  → ForgeInteractListener
    → ForgeStationService.resolveStationFromClick()
    → ForgeMenuService.open()
      → ForgeInventoryHolder (Bukkit inventory created)

Player clicks Confirm
  → ForgeInventoryListener (CHANGED)
    → ForgeService.confirmAndExecute()
      → Validate context
      → CostService.quote() — affordability check
      → CostService.charge() — deduct costs
      → Collect item custody
      → TierRequirements.validate() — check input items
      → ChanceTable.select() — random outcome
      → ForgeAnimationService.playAnimation()
        → On complete: OutcomeExecutor.execute()
          → SUCCESS: ItemMutationService.mutate()
          → BREAK: consume input
          → CURSE: applyCurse()
      → ForgeSession.close()
      → AuditLogService.logAsync()

Player disconnects mid-processing
  → PlayerLifecycleListener.onPlayerQuit()
    → ForgeService.onPlayerQuit()
      → Queue pending delivery
      → Refund if OPEN state
```

## Persistence

| Data               | Format | Location                              | Load Strategy       |
|--------------------|--------|---------------------------------------|--------------------|
| Plugin config      | YAML   | `config.yml`                          | Bukkit config API  |
| Station registry   | YAML   | `stations.yml`                        | Custom load/save   |
| Tier definitions   | YAML   | `tiers/*.yml` (schema v2)             | TierRepository.load() |
| Player state       | YAML   | `player-data/<uuid>.yml`              | Lazy load on join  |
| Pending deliveries | YAML   | `pending-deliveries.yml`              | Full load on start |
| Audit log          | JSONL  | `audit/YYYY-MM-DD.jsonl`              | Async append only  |

## Safety Guarantees

1. **Anti-dupe**: Transaction custody is held from cost charge until outcome delivery. If player is offline, delivery is queued.
2. **Crash-safe escrow**: Pending deliveries are persisted before confirmation. On restart, `DeliveryService.processGlobalContext()` resumes.
3. **Atomic session transitions**: Session state changes use compare-and-swap to prevent race conditions.
4. **Bounded audit queue**: Queue capacity is configurable; drops are logged with count.

## Material Compatibility Authority

`MaterialResolver` is the single authority for material resolution across the plugin. It provides:

- **Alias mapping**: Canonical names map to one or more Bukkit material candidates (e.g., `black_stained_glass_pane` → `BLACK_STAINED_GLASS_PANE` or `STAINED_GLASS_PANE:15`)
- **Legacy data support**: Syntax `MATERIAL_NAME:legacyData` parses and preserves legacy damage/data values
- **Caching**: Resolved materials are cached; repeated lookups return cached results
- **Fallback chains**: `itemOrThrow(int amount, String... candidates)` iterates candidates and returns the first valid material

Material resolution is used by:
- Tier outcome items (`ItemMutationService`)
- Menu icons (`MenuItemFactory`)
- Delivery items (`DeliveryService`)
- Forge station catalyst/ward materials

## Hologram System

### Provider Selection Architecture

`HologramProviderSelector.select()` iterates the configured `provider-order` list:

1. Each name must match a known provider (`FancyHolograms` or `DecentHolograms`)
2. `PluginManager.getPlugin(name)` resolves the plugin from the server's plugin registry
3. The plugin must be enabled and the provider must report availability
4. Provider availability is checked via the provider's own `isAvailable()` method

Selection is plugin-classloader-aware: class resolution for each provider uses that provider plugin's own `ClassLoader` to avoid class-not-found errors when a provider JAR is absent.

### FancyHolograms Integration

FancyHolograms integration uses reflection with no compile-time dependency:

1. `FancyHologramsApiBindings` resolves these classes via the provider plugin's classloader:
   - `de.oliver.fancyholograms.api.FancyHologramsPlugin` (v2 API) or `de.oliver.fancyholograms.FancyHolograms` (v1 compat)
   - `de.oliver.fancyholograms.api.HologramManager`
   - `de.oliver.fancyholograms.api.data.HologramData`
   - `de.oliver.fancyholograms.api.data.TextHologramData`
2. It resolves methods: `getHologram`, `create`, `addHologram`, `removeHologram`, `setText`, `setBackground`, `setPersistent`
3. It resolves the `TRANSPARENT` color constant via reflection
4. `FancyHologramsProvider` calls these methods to upsert/remove holograms at forge locations

### Dual Text Channels

`ForgeStationHologramService` renders hologram lines in two formats simultaneously:

| Provider | Text Format | Method |
|----------|-------------|--------|
| FancyHolograms | MiniMessage | `renderLinesFromTemplates(templates, placeholders, true)` |
| DecentHolograms | Legacy color codes | `renderLinesFromTemplates(templates, placeholders, false)` |

The `ForgeHologram` object carries both rendered line lists. Each provider selects the appropriate channel on upsert.

### Hologram Lifecycle Ownership

Hologram lifecycle is managed by `ForgeStationHologramService`:

1. **World resolution**: `scheduleAtForge()` resolves the forge world via UUID or name fallback using `Bukkit.getWorld()`
2. **Region operations**: All hologram upsert/remove calls run via `scheduler.runGlobal()` for world-level synchronization, then `scheduler.runRegion()` at the forge location for chunk-aware execution
3. **ID derivation**: Hologram IDs are `flameforge_<normalized_forge_id>`
4. **Startup reconcile**: `reconcileStartup()` creates holograms for all registered forges on plugin enable
5. **Dynamic add/remove**: `onStationAdded()` and `onStationRemoved()` handle lifecycle as stations are registered or unregistered
6. **Reload**: `reload()` clears the mapping, removes all old holograms via the old provider/settings, then re-selects provider and reconciles
