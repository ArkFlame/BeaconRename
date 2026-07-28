# FlameForge Administrator Guide

## Initial Setup

### 1. Install and First Run

Place the JAR in your server's `plugins/` directory and start the server. On first run, FlameForge will:

- Create `plugins/FlameForge/config.yml` with defaults
- Create `plugins/FlameForge/tiers/` directory
- Copy seven default tier files (`tier1.yml` through `tier7.yml`) into the tiers directory

### 2. Register Beacon Stations

Players must right-click a registered beacon to open the forge menu.

To register a station:
1. Build a beacon block
2. Stand within 6 blocks of it
3. Run: `/flameforge station add <station-id> [profile]`

Example:
```
/flameforge station add main_forge default
```

To remove a station:
```
/flameforge station remove main_forge
```

### 3. Configure Tiers

Edit the YAML files in `plugins/FlameForge/tiers/`. Each file defines one tier with outcomes, costs, and display options.

After editing, either:
- Restart the server, or
- Run `/flameforge reload`

### 4. Test the Forge

1. Right-click the registered beacon
2. Place an item in the input slot
3. Select a tier from the right-side buttons
4. Click the confirm button (bottom center)
5. Observe the animation and outcome

## Configuration Validation

Run `/flameforge validate` to check for configuration errors without reloading.

Validation checks:
- Schema version mismatches
- Missing required fields (`id`, `type`)
- Invalid material or enchantment names
- Duplicate tier IDs
- Weight values that are zero or negative

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

### Beacon Does Not Open Menu

**Symptom:** Right-clicking a beacon does nothing.

**Causes:**
- Station not registered (station mode is `REGISTERED_ONLY`)
- Player lacks required permission for the station's profile
- Tier selection not allowed (max-tier exceeded)

**Resolution:**
1. Verify station is registered: `/flameforge station list`
2. Check player permissions for the station profile
3. Verify the selected tier is within the profile's `max-tier`

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
  Tiers: 7 | Stations: 3 | Folia: yes | Vault/Economy: available | SMPWeapons: detected | Mode: normal
```

- `Mode: DEGRADED` indicates validation errors; features may not work correctly.

### Audit Log Format

Each entry in `audit/YYYY-MM-DD.jsonl`:

```json
{"timestamp":1234567890,"action":"FORGE_COMPLETE","actor":"PlayerName","target":"tx-id","details":"Outcome: legendary_sword, Type: CREATE_ITEM"}
```

Actions include: `FORGE_COMPLETE`, `ITEM_DELIVERED`, `COMMAND_DISPATCH`, `WARD_CONVERT`, `DELIVERY_QUEUED`, `ANNOUNCEMENT_GLOBAL`.

## Upgrade Notes

### From Pre-1.0 Builds

- Tier schema version is now `1`. Existing tier files will be validated against this schema.
- If validation errors appear, compare your tier files against the schema in `CONFIGURATION.md`.
- Tier files in `tiers/` are never overwritten on upgrade. Default tiers are only bootstrapped when the directory does not exist.

### Deleting Tiers

Deleting a tier file removes that tier from active configuration. Players currently in the menu may see the tier disappear on next render. Deleting all tiers results in an empty tier list; the plugin remains enabled.
