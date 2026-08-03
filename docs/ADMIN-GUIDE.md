# FlameForge Administrator Guide

## Initial Setup

### 1. Install and First Run

Place the JAR in your server's `plugins/` directory and start the server. On first run, FlameForge will:

- Create `plugins/FlameForge/config.yml` with defaults
- Create `plugins/FlameForge/tiers/` directory
- Copy seven default tier files (`tier1.yml` through `tier7.yml`) into the tiers directory

### 2. Register Forge Stations

Players must right-click a registered forge to open the forge menu.

To register a forge:
1. Target any non-air block; no specific material is required
2. Stand within 6 blocks of it
3. Run: `/flameforge station add [station-id] [profile]`

Example:
```
/flameforge station add main_forge default
```

The ID is optional. Any non-air block can be registered as a forge. In `REGISTERED_ONLY` mode, players open the menu by right-clicking the registered forge, not an arbitrary unregistered block.

To remove a station:
```
/flameforge station remove main_forge
```

### 3. Configure Tiers

Edit the YAML files in `plugins/FlameForge/tiers/`. Each file defines one tier with outcomes, costs, and display options. Tiers use schema v2 with `level` instead of `priority`.

After editing, either:
- Restart the server, or
- Run `/flameforge reload`

### 4. Test the Forge

1. Right-click the registered forge
2. Place an item in the input slot
3. Select a tier from the right-side buttons (automatic next-tier is supported)
4. Click the confirm button (bottom center)
5. Observe the animation and outcome

## New Menu Flow

The 27-slot forge menu has a streamlined layout:

```
┌─────────────────────────────────────┐
│  [Fill]              [Tier Buttons] │
│  [Fill]  [INPUT]     [Tier Buttons] │
│  [Fill]              [Tier Buttons] │
│  [Fill]              [Tier Buttons] │
│  [Fill]  [CONFIRM]   [Navigation ]  │
└─────────────────────────────────────┘
```

**Flow:**
1. Player places item in input slot (center)
2. Tier buttons on right show available tiers for the item
3. Clicking a tier button selects it (automatic next-tier progression if enabled)
4. Clicking confirm executes the forge
5. Animation plays, outcome is applied

**Removed slots:** Catalyst slot, ward slot, pity counter

## Configuration Validation

Run `/flameforge validate` to check for configuration errors without reloading.

Validation checks:
- Schema version mismatches (v2 required)
- Missing required fields (`id`, `type`, `level`)
- Invalid material or enchantment names
- Duplicate tier IDs
- Weight values that are zero or negative
- Tier requirement validation

Errors will prevent the tier from loading. Warnings indicate non-fatal issues.

## Backup

### What to Back Up

| Data                    | Location                                    |
|-------------------------|---------------------------------------------|
| Plugin config           | `plugins/FlameForge/config.yml`             |
| Station registry        | `plugins/FlameForge/stations.yml`           |
| Tier definitions        | `plugins/FlameForge/tiers/*.yml`            |
| Player state            | `plugins/FlameForge/player-data/*.yml`      |
| Pending deliveries      | `plugins/FlameForge/pending-deliveries.yml` |
| Custom messages         | `plugins/FlameForge/messages.yml`           |

### Audit Logs

Daily JSONL files in `plugins/FlameForge/audit/` are not critical path but can be archived for compliance.

### Backup Strategy

Stop the server before copying plugin data for consistent snapshots. The audit log writer will flush and close cleanly on `onDisable()`.

## Troubleshooting

### Plugin Will Not Start

**Symptom:** No "FlameForge ready" message in console.

**Causes:**
- Missing dependency (Vault if using money costs)
- Java version too old (requires Java 8+)
- Corrupt config.yml

**Resolution:** Check server logs for specific errors. Run `/flameforge validate` after fixing.

### Forge Does Not Open Menu

**Symptom:** Right-clicking a registered forge does nothing.

**Causes:**
- Station not registered (station mode is `REGISTERED_ONLY`)
- Player lacks required permission for the station's profile
- Tier selection not allowed (max-tier exceeded)
- Input item does not match tier requirements

**Resolution:**
1. Verify station is registered: `/flameforge station list`
2. Check player permissions for the station profile
3. Verify the selected tier is within the profile's `max-tier`
4. Ensure input item matches tier requirements

### Items Not Delivered

**Symptom:** Forge succeeds but player does not receive the new item.

**Causes:**
- Player inventory full
- Player disconnected during animation
- Delivery queued for offline player

**Resolution:**
- Pending deliveries are delivered on next player join
- Check `plugins/FlameForge/pending-deliveries.yml`
- Verify `DeliveryService` is processing on join

### Money/XP Not Deducted

**Symptom:** Forge succeeds without cost being charged.

**Causes:**
- Player has `flameforge.bypass.cost` permission
- Economy plugin not detected (no Vault)
- XP mode but player has insufficient levels

**Resolution:**
- Check player permissions
- Verify Vault is installed and an economy provider is active
- Check `config.yml` cost mode

### Duplicate Item Duplication (Anti-Dupe)

**Symptom:** Players report gaining items without losing the original.

**Resolution:** This should not occur. FlameForge uses a custody model:
1. Items are removed from input slots on confirm
2. Cost is charged immediately
3. If player disconnects, pending delivery is queued
4. On rejoin, pending deliveries are processed

If duplication occurs:
1. Stop the server immediately
2. Check `plugins/FlameForge/pending-deliveries.yml` for orphaned entries
3. Check audit logs for transaction anomalies
4. Report the issue with steps to reproduce

### Tier Not Appearing in Menu

**Symptom:** Created a tier file but it does not appear in the forge menu.

**Causes:**
- Tier file has a validation error (skipped during load)
- Tier ID conflict with another tier
- Input item does not match tier requirements
- Plugin not reloaded after file creation

**Resolution:**
1. Run `/flameforge validate` and check for errors
2. Verify tier file is in `plugins/FlameForge/tiers/`
3. Ensure file extension is `.yml`
4. Reload the plugin

### Configuration Reload Fails

**Symptom:** `/flameforge reload` does not complete.

**Causes:**
- Invalid YAML in a config file
- Tier file has critical errors

**Resolution:** Check server logs for parse errors. The previous configuration remains active if reload fails.

### Holograms Not Appearing

**Symptom:** Forge stations exist but no floating text displays appear above them.

**Causes:**
- No hologram provider plugin installed (FancyHolograms or DecentHolograms)
- `holograms.enabled` is `false` in config
- Provider plugin is disabled
- World not found for forge station

**Resolution:**
1. Verify a hologram provider is installed: FancyHolograms (v2+) or DecentHolograms
2. Check `plugins/FlameForge/config.yml` has `holograms.enabled: true`
3. Check server logs for `[FlameForge] Hologram provider:` message at startup
4. Verify forge station world exists and is loaded

**Provider detection log example:**
```
[FlameForge] Hologram provider: FancyHolograms v2.4.0
```

If you see `Hologram provider: disabled by configuration`, set `holograms.enabled: true`.

### Runtime Proof and Reload Behavior

FlameForge has two runtime proof mechanisms:

1. **Full server restart** — Primary proof. All state (station registry, hologram state, player sessions, pending deliveries) is fully reinitialized from disk on startup.
2. **PlugManX reload** — Secondary only. PlugManX `reload` calls the plugin's `onDisable()` and `onEnable()` which triggers internal reload. However:
   - Hologram provider re-detection may behave inconsistently if the provider plugin was also reloaded by PlugManX
   - Pending deliveries are reprocessed on join
   - Station registry is reloaded from disk

For guaranteed consistent state, prefer a full server restart over plugin manager reload.

### FancyHolograms API Integration

FlameForge integrates with FancyHolograms via reflection without a compile-time dependency:

- **Supported versions**: FancyHolograms v2+
- **API class**: `de.oliver.fancyholograms.api.FancyHologramsPlugin`
- **Fallback class**: `de.oliver.fancyholograms.FancyHolograms` (v1 compatibility)
- **Text format**: MiniMessage (supports gradients, rainbow, custom hex colors)
- **Transparent background**: Uses `Hologram.TRANSPARENT` color constant

FancyHolograms is resolved through its own plugin classloader to avoid class-not-found errors when the plugin is absent.

### Hologram Provider Selection

The `holograms.provider-order` list controls which library is used:

```yaml
holograms:
  provider-order:
    - FancyHolograms   # checked first
    - DecentHolograms   # fallback
```

Selection is first-match:
1. If `FancyHolograms` plugin is enabled and its API is available → FancyHolograms is used
2. Else if `DecentHolograms` plugin is enabled and its API is available → DecentHolograms is used
3. Else no holograms are created (no-op mode)

Unknown provider names in the list are skipped.

## Performance

### Large Player Counts

FlameForge uses:
- `ConcurrentHashMap` for station and player state caches
- Async file I/O for saves
- Dedicated daemon thread for audit log writing

For servers with 100+ concurrent players, ensure:
- Audit queue capacity is sufficient (default 1024)
- Tier file count is reasonable (each tier requires menu rendering time)
- Station count is reasonable (station lookup is O(n) on the snapshot)

### Folia Considerations

On Folia servers:
- Teleportation uses entity schedulers
- Player join processing uses entity schedulers
- Chunk access is region-aware

## Log Interpretation

### Startup Log

```
[FlameForge] v1.0.0 ready
  Tiers: 7 | Stations: 3 | Folia: yes | Vault/Economy: available | Mode: normal
```

- `Mode: DEGRADED` indicates validation errors; features may not work correctly.

### Audit Log Format

Each entry in `audit/YYYY-MM-DD.jsonl`:

```json
{"timestamp":1234567890,"action":"FORGE_COMPLETE","actor":"PlayerName","target":"tx-id","details":"Outcome: legendary_sword, Type: MODIFY_INPUT, Category: SUCCESS"}
```

Actions include: `FORGE_COMPLETE`, `ITEM_DELIVERED`, `COMMAND_DISPATCH`, `DELIVERY_QUEUED`, `ANNOUNCEMENT_GLOBAL`.

## Upgrade Notes

### From Pre-1.0 Builds

- Tier schema version is now `2`. Existing tier files will be validated against this schema.
- `priority` field replaced by `level` for automatic tier progression.
- Catalyst and ward configuration removed; use tier requirements instead.
- Pity system removed from UI; configured per-tier if needed.
- If validation errors appear, compare your tier files against the schema in `CONFIGURATION.md`.
- Tier files in `tiers/` are never overwritten on upgrade. Default tiers are only bootstrapped when the directory does not exist.
- `tier-migration.auto-upgrade: true` in config.yml will attempt automatic conversion.

### Deleting Tiers

Deleting a tier file removes that tier from active configuration. Players currently in the menu may see the tier disappear on next render. Deleting all tiers results in an empty tier list; the plugin remains enabled.
