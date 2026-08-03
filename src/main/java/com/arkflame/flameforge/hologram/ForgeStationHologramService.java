package com.arkflame.flameforge.hologram;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.persistence.StationRepository.RegisteredForge;
import com.arkflame.flameforge.station.StationIdPolicy;
import com.arkflame.flameforge.text.TextRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class ForgeStationHologramService {

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final StationRepository stationRepository;
    private final ConfigService configService;
    private final TextRenderer textRenderer;
    private volatile HologramProvider provider;
    private volatile HologramSettings settings;
    private HologramProviderSelector selector;
    private final ConcurrentHashMap<String, String> hologramIdByForgeId = new ConcurrentHashMap<>();
    private final AtomicBoolean startupReconciled = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong(0);

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
        this.selector = null;
        this.provider = Objects.requireNonNull(provider);
        this.settings = Objects.requireNonNull(settings);
    }

    public void reconcileStartup() {
        if (!startupReconciled.compareAndSet(false, true)) {
            return;
        }
        if (!settings.isEnabled()) {
            plugin.getLogger().info("[Hologram] disabled explicitly by config");
            return;
        }
        if (!provider.isAvailable()) {
            plugin.getLogger().info("[Hologram] enabled but no compatible provider: " + provider.getUnavailableReason());
            return;
        }

        List<RegisteredForge> forges = stationRepository.snapshotSortedById();
        for (RegisteredForge forge : forges) {
            String hologramId = deriveHologramId(forge.getId());
            hologramIdByForgeId.put(forge.getId(), hologramId);
            scheduleAtForge(forge, provider, settings, location -> upsertHologram(hologramId, location, forge));
        }
    }

    public void onStationAdded(RegisteredForge forge) {
        String hologramId = deriveHologramId(forge.getId());
        hologramIdByForgeId.put(forge.getId(), hologramId);

        if (!settings.isEnabled() || !provider.isAvailable()) {
            return;
        }

        scheduleAtForge(forge, provider, settings, location -> upsertHologram(hologramId, location, forge));
    }

    public void onStationRemoved(RegisteredForge forge) {
        String forgeId = forge.getId();
        String hologramId = hologramIdByForgeId.remove(forgeId);
        if (hologramId == null) {
            return;
        }
        HologramProvider oldProvider = provider;
        HologramSettings oldSettings = settings;
        long currentGeneration = generation.get();
        scheduleAtForge(forge, oldProvider, oldSettings, location -> oldProvider.remove(hologramId));
    }

    private void scheduleAtForge(RegisteredForge forge, HologramProvider targetProvider,
                                  HologramSettings targetSettings, Consumer<Location> regionOperation) {
        scheduler.runGlobal(plugin, () -> {
            World world = null;
            if (forge.getWorldUuid() != null) {
                world = Bukkit.getWorld(forge.getWorldUuid());
            }
            if (world == null && forge.getWorldName() != null) {
                world = Bukkit.getWorld(forge.getWorldName());
            }
            if (world == null) {
                plugin.getLogger().warning("[Hologram] World not found for forge: " + forge.getId());
                return;
            }
            Location centered = new Location(world, forge.getX() + 0.5,
                forge.getY() + targetSettings.getOffsetY(), forge.getZ() + 0.5);
            regionOperation.accept(centered.clone());
        });
    }

    public void updateHologram(String forgeId, Location forgeLocation, List<String> lines) {
        String hologramId = hologramIdByForgeId.get(forgeId);
        if (hologramId != null && provider.isAvailable() && forgeLocation != null) {
            List<String> renderedLines = renderLines(lines);
            scheduler.runRegion(forgeLocation, () -> {
                ForgeHologram hologram = new ForgeHologram(
                    hologramId, forgeLocation, renderedLines, renderedLines, settings.isTransparentBackground());
                provider.upsert(hologram);
            });
        }
    }

    public void reload() {
        generation.incrementAndGet();
        HologramProvider oldProvider = provider;
        HologramSettings oldSettings = settings;
        Map<String, String> oldMapping = new ConcurrentHashMap<>(hologramIdByForgeId);

        for (Map.Entry<String, String> entry : oldMapping.entrySet()) {
            String forgeId = entry.getKey();
            String hologramId = entry.getValue();
            stationRepository.findById(forgeId).ifPresent(forge -> {
                scheduleAtForge(forge, oldProvider, oldSettings, location -> oldProvider.remove(hologramId));
            });
        }

        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        HologramSettings newSettings = HologramSettings.fromSnapshot(snapshot);
        HologramProvider newProvider = selector.select(newSettings);

        this.settings = newSettings;
        this.provider = newProvider;
        hologramIdByForgeId.clear();
        startupReconciled.set(false);
        reconcileStartup();
    }

    public void disableCleanup() {
        generation.incrementAndGet();
        HologramProvider oldProvider = provider;
        HologramSettings oldSettings = settings;
        Map<String, String> oldMapping = new ConcurrentHashMap<>(hologramIdByForgeId);

        for (Map.Entry<String, String> entry : oldMapping.entrySet()) {
            String forgeId = entry.getKey();
            String hologramId = entry.getValue();
            stationRepository.findById(forgeId).ifPresent(forge -> {
                scheduleAtForge(forge, oldProvider, oldSettings, location -> oldProvider.remove(hologramId));
            });
        }
        hologramIdByForgeId.clear();
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

    private String deriveHologramId(String forgeId) {
        return "flameforge_" + StationIdPolicy.normalize(forgeId);
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

    private List<String> renderLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> rendered = new java.util.ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(textRenderer.renderToLegacy(line));
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
        return provider.getName() + " " + provider.getVersion();
    }
}
