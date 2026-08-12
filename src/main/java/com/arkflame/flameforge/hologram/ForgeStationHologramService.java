package com.arkflame.flameforge.hologram;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.persistence.StationRepository.RegisteredForge;
import com.arkflame.flameforge.text.TextRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ForgeStationHologramService {

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final StationRepository stationRepository;
    private final ConfigService configService;
    private final TextRenderer textRenderer;
    private final Logger logger;
    private volatile HologramProvider provider;
    private volatile HologramSettings settings;
    private HologramProviderSelector selector;
    private final ConcurrentHashMap<String, String> hologramIds = new ConcurrentHashMap<>();
    private final AtomicBoolean startupReconciled = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, AtomicBoolean> forgeHydrated = new ConcurrentHashMap<>();

    public ForgeStationHologramService(JavaPlugin plugin, SchedulerBridge scheduler,
                                       StationRepository stationRepository,
                                       ConfigService configService, TextRenderer textRenderer,
                                       HologramProviderSelector selector,
                                       HologramProvider provider, HologramSettings settings) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.stationRepository = Objects.requireNonNull(stationRepository);
        this.configService = Objects.requireNonNull(configService);
        this.textRenderer = Objects.requireNonNull(textRenderer);
        this.logger = plugin.getLogger();
        this.selector = Objects.requireNonNull(selector);
        this.provider = Objects.requireNonNull(provider);
        this.settings = Objects.requireNonNull(settings);
    }

    public ForgeStationHologramService(JavaPlugin plugin, SchedulerBridge scheduler,
                                       StationRepository stationRepository,
                                       ConfigService configService, TextRenderer textRenderer,
                                       HologramProvider provider, HologramSettings settings) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.stationRepository = Objects.requireNonNull(stationRepository);
        this.configService = Objects.requireNonNull(configService);
        this.textRenderer = Objects.requireNonNull(textRenderer);
        this.logger = plugin.getLogger();
        this.selector = null;
        this.provider = Objects.requireNonNull(provider);
        this.settings = Objects.requireNonNull(settings);
    }

    private boolean ensureProviderAvailable(String trigger) {
        HologramProvider current = provider;
        if (current.isAvailable()) {
            return true;
        }

        logger.info("Hologram provider unavailable for " + trigger + ": " + current.getUnavailableReason());

        if (selector == null) {
            return false;
        }

        HologramProvider reselected = selector.select(settings);
        provider = reselected;

        if (!reselected.isAvailable()) {
            logger.info("Reselected provider still unavailable for " + trigger + ": " + reselected.getUnavailableReason());
            return false;
        }

        logger.info("Selected hologram provider: " + reselected.getClass().getSimpleName());
        return true;
    }

    private World resolveWorld(RegisteredForge forge) {
        if (forge.getWorldUuid() != null) {
            World world = Bukkit.getWorld(forge.getWorldUuid());
            if (world != null) {
                return world;
            }
        }
        if (forge.getWorldName() != null) {
            return Bukkit.getWorld(forge.getWorldName());
        }
        return null;
    }

    private Location buildLocation(World world, RegisteredForge forge, HologramSettings targetSettings) {
        return new Location(world, forge.getX() + 0.5, forge.getY() + targetSettings.getOffsetY(), forge.getZ() + 0.5);
    }

    public void reconcileStartup() {
        if (startupReconciled.get()) {
            return;
        }

        if (!settings.isEnabled()) {
            if (startupReconciled.compareAndSet(false, true)) {
                logger.info("Hologram support is disabled");
            }
            return;
        }

        if (!ensureProviderAvailable("startup")) {
            return;
        }

        if (!startupReconciled.compareAndSet(false, true)) {
            return;
        }

        List<RegisteredForge> stations = stationRepository.snapshotSortedById();
        for (RegisteredForge forge : stations) {
            String hologramId = forge.getId() + "_hologram";
            hologramIds.put(forge.getId(), hologramId);
            forgeHydrated.put(forge.getId(), new AtomicBoolean(true));
            scheduleAtForge(forge, provider, settings, location -> {
                upsertHologram(hologramId, location, forge);
            });
        }
    }

    public void onStationAdded(RegisteredForge forge) {
        stationAdd(forge);
    }

    public void stationAdd(RegisteredForge forge) {
        if (!settings.isEnabled()) {
            return;
        }

        String hologramId = forge.getId() + "_hologram";
        hologramIds.put(forge.getId(), hologramId);
        forgeHydrated.put(forge.getId(), new AtomicBoolean(true));

        if (!ensureProviderAvailable("station-add")) {
            return;
        }

        scheduleAtForge(forge, provider, settings, location -> {
            upsertHologram(hologramId, location, forge);
        });
    }

    public void onStationRemoved(RegisteredForge forge) {
        stationRemove(forge);
    }

    public void stationRemove(RegisteredForge forge) {
        String hologramId = hologramIds.remove(forge.getId());
        forgeHydrated.remove(forge.getId());

        if (hologramId == null) {
            return;
        }

        if (provider == null || !provider.isAvailable()) {
            return;
        }

        scheduleAtForge(forge, provider, settings, location -> {
            provider.remove(hologramId);
        });
    }

    public void update(RegisteredForge forge) {
        String hologramId = hologramIds.get(forge.getId());
        if (hologramId == null) {
            return;
        }

        if (!settings.isEnabled()) {
            return;
        }

        if (!ensureProviderAvailable("update")) {
            return;
        }

        final String finalHologramId = hologramId;
        scheduleAtForge(forge, provider, settings, location -> {
            upsertHologram(finalHologramId, location, forge);
        });
    }

    private void scheduleAtForge(RegisteredForge forge, HologramProvider targetProvider,
                                  HologramSettings targetSettings, Consumer<Location> operation) {
        scheduler.runGlobal(plugin, () -> {
            World w = resolveWorld(forge);
            if (w == null) {
                return;
            }
            Location centered = buildLocation(w, forge, targetSettings);
            scheduler.runRegion(centered, () -> {
                try {
                    operation.accept(centered);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Hologram] Failed operation for forge " + forge.getId(), e);
                    forgeHydrated.remove(forge.getId());
                }
            });
        });
    }

    public void reload(HologramSettings newSettings) {
        for (String hologramId : new HashSet<>(hologramIds.values())) {
            if (provider != null && provider.isAvailable()) {
                try {
                    provider.remove(hologramId);
                } catch (Exception e) {
                }
            }
        }
        hologramIds.clear();
        forgeHydrated.clear();

        settings = newSettings;
        provider = selector.select(settings);
        startupReconciled.set(false);

        reconcileStartup();
    }

    public void reload() {
        reload(settings);
    }

    public void disableCleanup() {
        for (String hologramId : new HashSet<>(hologramIds.values())) {
            if (provider != null && provider.isAvailable()) {
                try {
                    provider.remove(hologramId);
                } catch (Exception e) {
                    logger.warning("Failed to remove hologram during cleanup: " + e.getMessage());
                }
            }
        }
        hologramIds.clear();
        forgeHydrated.clear();
    }

    private void upsertHologram(String hologramId, Location location, RegisteredForge forge) {
        Map<String, String> placeholders = buildPlaceholders(forge);
        List<String> miniMessageLines = renderLinesFromTemplates(settings.getLineTemplates(), placeholders, true);
        List<String> legacyLines = renderLinesFromTemplates(settings.getLineTemplates(), placeholders, false);
        ForgeHologram hologram = new ForgeHologram(
            hologramId, location, miniMessageLines, legacyLines, settings.isTransparentBackground());
        provider.upsert(hologram);
    }

    private Map<String, String> buildPlaceholders(RegisteredForge forge) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("forge_id", forge.getId());
        placeholders.put("profile", forge.getProfileId());
        placeholders.put("world", forge.getWorldName());
        placeholders.put("x", Integer.toString(forge.getX()));
        placeholders.put("y", Integer.toString(forge.getY()));
        placeholders.put("z", Integer.toString(forge.getZ()));
        return placeholders;
    }

    private List<String> renderLinesFromTemplates(List<String> templates, Map<String, String> placeholders, boolean miniMessage) {
        if (templates == null || templates.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> rendered = new java.util.ArrayList<>(templates.size());
        for (String template : templates) {
            if (miniMessage) {
                rendered.add(textRenderer.renderToMiniMessage(template, placeholders, Collections.emptyMap(), "holograms.lines"));
            } else {
                rendered.add(textRenderer.renderToLegacy(template, placeholders, Collections.emptyMap(), "holograms.lines"));
            }
        }
        return rendered;
    }

    public String getProviderStatus() {
        if (!settings.isEnabled()) {
            return "disabled (config)";
        }
        if (!provider.isAvailable()) {
            return "unavailable (" + provider.getUnavailableReason() + ")";
        }
        return provider.getClass().getSimpleName();
    }
}
