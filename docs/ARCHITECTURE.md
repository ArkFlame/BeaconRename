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
| `TierDefinition`         | Immutable tier: id, priority, cost, outcomes              |
| `TierParser`             | YAML parsing into TierDefinition + TierExtra              |
| `StationRepository`      | Station persistence (stations.yml)                        |
| `ForgeStationService`    | Station resolution, permission checks, teleport           |
| `ForgeSessionService`    | Player session state machine                              |
| `ForgeSession`           | OPEN → PROCESSING → SETTLING → CLOSED                    |
| `ForgeContext`           | Immutable snapshot of forge parameters at confirm time    |
| `ForgeTransaction`       | Atomic record: custody, charge, chance table, outcome     |
| `OutcomeExecutor`        | Dispatches outcome effects: BREAK, MODIFY, CREATE, etc.  |
| `ItemMutationService`    | Applies enchantments, attributes, name, lore to ItemStack  |
| `ItemMatcher`            | Matches ItemStack against ItemMatcherSpec                |
| `PlayerStateRepository`  | YAML-based player state persistence                       |
| `AuditLogService`        | Async JSONL writer with bounded queue                      |
| `PendingDeliveryRepository` | Queues items/commands for offline/delayed delivery      |

### Outcome Types

| Enum Value       | Behavior                                                       |
|------------------|----------------------------------------------------------------|
| `BREAK`          | Input item is consumed, no output                              |
| `RETURN_UNCHANGED` | Input item returned unchanged                                |
| `MODIFY_INPUT`   | Input item mutated per ItemMutationSpec, returned              |
| `CREATE_ITEM`    | New item created per ItemMutationSpec, delivered               |
| `COMMANDS`       | Console commands dispatched with placeholders                  |

### Item Identity System

`ItemIdentityService` writes and reads a PersistentDataContainer (PDC) tag on items to track:
- Forge origin station
- Reforge count
- Material group membership

This enables:
- PDC-based item matching in tier requirements
- Reforge count bounds in item matching
- Material group aliases

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
Player clicks beacon
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
      → ChanceTable.select() — random outcome
      → ForgeAnimationService.playAnimation()
        → On complete: OutcomeExecutor.execute()
          → MODIFY_INPUT: ItemMutationService.mutate()
          → CREATE_ITEM: DeliveryService.deliverItem()
          → COMMANDS: scheduler.runGlobal() dispatch
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
| Tier definitions   | YAML   | `tiers/*.yml`                         | TierRepository.load() |
| Player state       | YAML   | `player-data/<uuid>.yml`              | Lazy load on join  |
| Pending deliveries | YAML   | `pending-deliveries.yml`              | Full load on start |
| Audit log          | JSONL  | `audit/YYYY-MM-DD.jsonl`              | Async append only  |

## Safety Guarantees

1. **Anti-dupe**: Transaction custody is held from cost charge until outcome delivery. If player is offline, delivery is queued.
2. **Crash-safe escrow**: Pending deliveries are persisted before confirmation. On restart, `DeliveryService.processGlobalContext()` resumes.
3. **Atomic session transitions**: Session state changes use compare-and-swap to prevent race conditions.
4. **Bounded audit queue**: Queue capacity is configurable; drops are logged with count.
