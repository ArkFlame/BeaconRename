# FlameForge Outcomes and Hooks

This document describes forge outcomes, item power semantics, particles,
armor reduction, and the optional integrations FlameForge actually uses.

## Forge Outcomes

Every forge attempt resolves to exactly one of three outcome categories:

| Category | Meaning                                             |
|----------|-----------------------------------------------------|
| SUCCESS  | Input item returned in mutated form (variant applied) |
| BREAK    | Break policy applied (reset/strip; may destroy the item) |
| CURSE    | Item encoded as cursed and can no longer be forged    |

Break/curse behavior is configured per tier (`break` / `curse` sections of the
tier file). Chances are configured per tier (`chances.success/break/curse`).
Forge costs and station cooldowns apply to the attempt itself; variants are
selected from eligible variants by weight.

### Item state and result messages

- `SUCCESS` replaces prior FlameForge metadata with the selected variant and
  returns the item. The success message renders the mutated display name,
  including its configured colors.
- `BREAK` follows the tier's break policy. A non-destroyed fractured item keeps
  its fractured colored display and can be submitted again under normal item
  policy; a destroyed result has no item output. The menu distinguishes
  `forge.confirm.fractured` from `forge.confirm.shattered`.
- `CURSE` strips prior FlameForge powers, FlameForge attributes, and FlameForge
  lore, then writes an encoded cursed identity. Cursed state is terminal and
  inspection rejects it before normal forging. This does not broadly remove
  arbitrary potion effects from a player or item.
- A malformed or stale encoded FlameForge identity is runtime-quarantined as
  `INVALID_IDENTITY` and denied rather than treated as a fresh item.
- Tier-0 custom name, lore, model data, or foreign persistent data protection
  applies to fresh unowned items. An owned fractured item can reforge under
  normal policy; a fresh foreign tier-0 custom item remains protected.

### Result themes (animation palettes)

`ForgeAnimationThemeResolver` picks the animation theme from the outcome
category and the used variant's powers/attributes:

The table below describes forge-result animation defaults. Combat chain trails
use the separate semantic families documented under Particle semantics; the
electric chain trail does not use a firework spark.

| Theme      | Triggering power/attribute                                      | Primary color | Particle |
|------------|-----------------------------------------------------------------|---------------|----------|
| electric   | `ON_HIT_CHAIN_DAMAGE`, `EVERY_N_HIT_LIGHTNING`                  | yellow (250,204,21) | flame / firework |
| explosive  | `ON_HIT_EXPLOSIVE`                                              | orange (249,115,22) | flame / firework |
| contagion  | `ON_HIT_CHAIN_POTION`                                           | lime (132,204,22)  | flame / firework |
| poison     | `ON_HIT_POTION` with POISON effect candidate                    | green (34,197,94)  | flame / firework |
| bleed      | `ON_HIT_BLEED`                                                  | red (220,38,38)    | flame / firework |
| swift      | `SHIFT_RIGHT_CLICK_DASH`, `PASSIVE_POTION` SPEED                | blue (56,189,248)  | flame / firework |
| heal       | `ON_HIT_HEAL`, `SHIFT_RIGHT_CLICK_HEAL`, `ON_BLOCK_HEAL`, `PASSIVE_POTION` REGENERATION | pink (244,114,182) | flame / firework |
| defensive  | `ON_BLOCK_POTION`, `ON_BLOCK_KNOCKBACK`, `ON_BLOCK_HEAL`, any DAMAGE_REDUCTION_* attribute | blue (96,165,250) | flame / firework |
| break      | BREAK outcome                                                   | red (239,68,68)    | smoke / crit |
| curse      | CURSE outcome                                                   | purple (168,85,247)| portal / spell |
| success    | no matching special theme                                       | amber (245,158,11)| flame / firework |

## Power Semantics

Powers are defined per variant (`variants.<id>.powers`) and stored on the item
identity as active power ids. Activation is restricted to the power's
`activation-slots` (MAIN_HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET, INVENTORY).
An empty slot list means "held in either hand".

### Power types

- **ON_HIT_POTION / ON_HIT_FIRE / ON_HIT_HEAL** — trigger on attack against any
  LivingEntity when the chance roll passes and the per-player power cooldown
  (per forge id) has expired. Potion applies to the victim; fire sets the
  victim's fire ticks; heal heals the attacker. For configured fire powers, a
  lethal hit guarantees primary ignition before the chance roll; a chance miss
  does not force area-of-effect fire on nearby entities.
- **EVERY_N_HIT_LIGHTNING / EVERY_N_HIT_KNOCKBACK** — a per-player/per-forge
  hit counter increments on each eligible hit; on the `hit-interval`-th hit the
  counter resets, the chance is rolled, and lightning strikes the victim's
  location (region-scheduled, no terrain damage) or the victim is knocked back
  away from the attacker.
- **ON_HIT_AOE_FIRE** — radial hop: the primary victim and every nearby
  LivingEntity inside `radius` get fire ticks (see Radial vs chain below).
- **ON_HIT_BLEED** — damage pulses: `pulse-count` hits of `damage-amount` on
  the victim at `pulse-interval-ticks` spacing (entity-scheduled; stops on
  death).
- **ON_HIT_EXPLOSIVE** — radial damage: primary target takes full
  `damage-amount`, each additional target takes
  `damage-amount × secondary-damage-multiplier`; targets receive an upward
  velocity of `primary-knockback-multiplier` one tick later. **No terrain
  damage** — no block explosion is created.
- **ON_HIT_CHAIN_POTION / ON_HIT_CHAIN_DAMAGE** — true chain hops (see below).
- **ON_BLOCK_POTION / ON_BLOCK_KNOCKBACK / ON_BLOCK_HEAL** — trigger when the
  defender is blocking (`Player.isBlocking`) and the equipped forged item is in
  an activation slot. Potion is applied to the attacker, knockback pushes the
  attacker away, heal heals the defender.
- **PASSIVE_POTION** — while a forged item with this power is in an activation
  slot (INVENTORY counts via the cached inventory forge ids), `duration-ticks`
  supplies an internal short refresh lease. It is not a visible effect
  lifetime; lore describes conditions such as Resistance I while held or
  Regeneration I while in inventory.
- **SHIFT_RIGHT_CLICK_DASH / SHIFT_RIGHT_CLICK_HEAL** — activated by
  sneak + right-click (air or block) with the forged item in the interacted
  hand; dash launches the player along their facing direction
  (`horizontal-strength`, `vertical-strength`), heal restores
  `heal-amount` up to max health.

### Radial vs true chain hops

- **Radial** (`executeRadial`, used by Scorching/AOE-fire and Explosive): the
  initial target is struck, then all nearby LivingEntities within `radius` are
  collected, distance-sorted, and each struck once — geometry is a fan-out
  from the impact point, not a hop sequence.
- **True chain** (`executeChain`, used by contagion/electric chain powers):
  hop semantics A→B→C: after the current target is struck, candidates near the
  current target are discovered and the *next* target is scheduled with a
  `chain-delay-ticks` delay. Each parent-hop edge renders as a straight line
  using five frames at delays 0/2/4/6/8 ticks; it is a visual network of
  parent-to-child segments, not a curved or particle-spark promise.
- **Deduplication**: the attacker's uuid and every visited target uuid are
  tracked in a shared visited set; no entity is struck twice and the attacker
  is never a target.
- **Caps**: `maxTargets` is validated to 1..16. Shipped chain powers use caps
  of 8 (`weapon_tier3`/`tier5` chain potion, `weapon_tier4`/`tier5` chain
  damage, `tier6` legacy contagion) and 10 (`weapon_tier6`/`weapon_tier7` chain
  damage and chain potion, `tier7` legacy chain damage). Radial powers ship at
  4–6 targets (AOE fire 4–5, explosive 5–6). `radius` caps at 16.
- Direct poison (`ON_HIT_POTION` with POISON candidate) applies to any
  LivingEntity victim — no hop involved.

### Particle semantics

- Every shipped power carries `particle-candidates`; candidates are resolved
  through runtime-safe semantic families. Fire uses `FLAME`/`LAVA`, direct
  poison uses a green family, lightning uses the electric family, bleed uses
  `CRIT`, explosive uses `EXPLOSION*`, heal uses `HEART`, speed uses
  `CLOUD`/`INSTANT_EFFECT`, resistance uses `ENCHANT`/`SPELL`, and wither uses
  `WITCH`/`LARGE_SMOKE`.
- Chain damage uses yellow dust with `END_ROD`/`ENCHANT`/`NOTE`/`CRIT`; it does
  not request a firework spark. Chain poison uses green dust with
  `SPELL`/`HAPPY_VILLAGER`/`CRIT`. Colored dust is used when available, with
  runtime fallbacks otherwise.
- Bleed activation and each pulse show redstone block-break visuals, red dust,
  and CRIT.
- **Cosmetic failure never aborts a power**: particle/colored-dust errors are
  caught per-effect and only logged; the power effect itself still executes.
- Passive potions emit particles only on activation and on each scheduled lease
  refresh; there is no continuous particle loop.

### Dynamic particle architecture

- Result and power semantics select a `ParticleStyle` from
  `ParticleStyleCatalog`. A style is an RGB palette plus an ordered candidate
  list; it is not a promise that one Bukkit enum exists on every server.
- Chain powers use the ordered network path. `MultiStrikeService` selects the
  electric or contagion network style, and `ParticleNetworkRenderer` renders
  each parent-to-child edge as straight interpolated geometry in five frames
  at delays 0, 2, 4, 6, and 8 ticks. Each receiving `Player` owns its scheduled
  spawn through `ParticleBridge`; this is not a global broadcast or a curved
  particle-spark promise.
- Runtime particle enum names and keys are indexed from the running API.
  `getDataType()` chooses the payload contract, including the 1.21.8 `NONE`
  versus 1.21.9 `Spell` effect distinction. Typed adaptation is attempted
  only when the request payload matches; unknown typed data uses
  `CustomPayload`, then the ordered candidate and provider fallbacks continue.
  Legacy `Effect` is the older provider path.
- Pattern math is pure and bounded. Particle points become requests, related
  requests are grouped into batches, and entity-owned scheduling performs the
  actual send. A failure in a candidate, typed adapter, provider, or cosmetic
  batch is isolated and logged at the compatibility boundary; the combat or
  forge power still executes.

### Passive activation and reconciliation

- Passive powers are resolved from the tier/variant definitions at activation
  time (`refreshPassivePowers`): inventory contents, armor contents, main hand
  and off hand are scanned once, valid forge identities collected, and
  PASSIVE_POTION powers started.
- Re-scanning is **event-driven, not periodic**: join, respawn, item-held,
  inventory click/drag/close, item drop/pickup, consume and item-break events
  queue a single delayed refresh per player (deduplicated by cancelling the
  prior queued task). There is no recurring full-scan task.
- An INVENTORY activation slot is satisfied by the cached set of forge ids
  currently in the player's inventory (`hasCachedInventoryForgeId`), refreshed
  by the same event pipeline.
- Quit clears cooldowns, passive tasks, the inventory cache and hit counters
  for that player.

### Cooldown display vs internal units

Power cooldowns are authored and enforced in `cooldown-ticks` (ticks); the
cooldown check is `cooldownTicks × 50ms` against a monotonic clock, keyed by
player + forge id + power id, with a 4096-entry eviction cap (configurable via
`forge.power-cooldown-max-entries`). Tier cooldowns use `cooldown-seconds`
and are displayed in seconds (e.g. `/flameforge tier info`). Variant lore
cooldown text for positive cooldowns uses seconds (e.g. 40 ticks →
"Cooldown: 2s"); zero means no cooldown.

## Armor Reduction Composition

Identity records carry semantic reduction attributes:
`DAMAGE_REDUCTION_PERCENT` (generic), `POISON_DAMAGE_REDUCTION_PERCENT`,
`MAGIC_DAMAGE_REDUCTION_PERCENT`, `FALL_DAMAGE_REDUCTION_PERCENT`, and
`ATTACK_DAMAGE_FLAT` (flat bonus).

- On non-entity damage (`EntityDamageEvent` not caused by an entity), the
  listener computes the best generic reduction across all six equipment slots
  plus the best cause-specific reduction matching the damage cause
  (POISON → poison %, MAGIC / DRAGON_BREATH → magic %, FALL → fall %), and
  composes them with a **hard cap of 80%**:
  `min(genericMax + specificMax, 0.80)`.
- The attack bonus path (`ATTACK_DAMAGE_FLAT`) is applied event-side only when
  modern attribute APIs are unavailable; on servers with modern attributes the
  native `AttributeBridge` receives only `ATTACK_DAMAGE_FLAT` and the event
  path is skipped, avoiding double application.
- A blue enchant/aura particle (`emitArmorReductionParticle`) is shown on the
  victim when any reduction applies.

## Offhand Support

The offhand is read through `EquipmentBridge`: it probes
`Player.getItemInOffhand()` at runtime; on 1.8.x (no offhand API) it falls back
to AIR, so offhand-slot powers simply never activate there. On servers with
`PlayerSwapHandItemsEvent`, the bridge registers a reflective MONITOR listener
so offhand swaps also trigger the event-driven passive reconciliation.
Interaction-hand detection (`InteractionHandBridge`) resolves whether a
sneak-right-click used the main or off hand.

## Hooks

### Vault (real integration)

Vault is a soft dependency (`plugin.yml`) and the VaultAPI is a provided
compile dependency. `EconomyServiceFactory` returns `VaultEconomyService` when
Vault is enabled **and** a registered `net.milkbowl.vault.economy.Economy`
provider exists; otherwise `NoEconomyService` is used. Vault money
withdrawals/deposits/formatting flow through `CostService` for money
requirements and money-based forge costs. Without Vault (or without an economy
provider) money requirements are reported unavailable and money costs cannot
be charged.

### Holograms — FancyHolograms / DecentHolograms

Hologram provider selection (`HologramProviderSelector`) reads
`holograms.provider-order` from config.yml (default: FancyHolograms,
DecentHolograms) and instantiates `FancyHologramsProvider` (reflective
FancyHolograms API bindings) or `DecentHologramsProvider` (reflective
DecentHolograms bindings) for the first plugin that is actually installed.
Both are soft dependencies. If none is installed, a `NoOpHologramProvider`
logs the reason and station holograms are skipped. Holograms are upserted per
station id, reconciled after load/reload, and cleaned up on disable.

### PacketEvents (hard dependency, external)

PacketEvents Spigot 2.13.0 is a **provided** (external) dependency and a hard
`depend` in plugin.yml — the server must run PacketEvents. FlameForge uses it
only for the forge animation: a fake item entity is spawned per viewer
(spawn + metadata packets), orbited/raised along a 6-rotation rising path with
a 1080° yaw sweep, then held and destroyed. Double spiral strands + trail +
connector + colored aura particles accompany the flight; a five-point star
(with halo) reveal renders the outcome. No real item entity is spawned and no
item is dropped; the actual item is delivered through the menu session.

### PlaceholderAPI

FlameForge does **not** hook PlaceholderAPI. All placeholders are the plugin's
own MiniMessage template variables (`%player_name%`, `%tier_id%`, …) resolved
by its internal `TextRenderer`.
