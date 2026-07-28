package com.arkflame.flameforge.config;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigService {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final TierRepository tierRepository;
    private final AtomicReference<ConfigSnapshot> currentSnapshot;
    private volatile ConfigSnapshot previousSnapshot;
    private final Object reloadLock = new Object();

    private volatile TaskHandle pendingReloadTask;

    public ConfigService(JavaPlugin plugin, SchedulerBridge scheduler, TierRepository tierRepository) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.tierRepository = tierRepository;
        this.currentSnapshot = new AtomicReference<>();
    }

    public void initialLoad() {
        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        ConfigSnapshot snapshot = buildSnapshot();
        currentSnapshot.set(snapshot);
    }

    public void asyncReload() {
        synchronized (reloadLock) {
            if (pendingReloadTask != null) {
                pendingReloadTask.cancel();
                pendingReloadTask = null;
            }

            pendingReloadTask = scheduler.runAsync(plugin, () -> {
                performReload();
                synchronized (reloadLock) {
                    pendingReloadTask = null;
                }
            });
        }
    }

    public void asyncReloadWithCallback(Runnable onComplete) {
        synchronized (reloadLock) {
            if (pendingReloadTask != null) {
                pendingReloadTask.cancel();
                pendingReloadTask = null;
            }

            pendingReloadTask = scheduler.runAsync(plugin, () -> {
                performReload();
                scheduler.runGlobalLater(plugin, onComplete, 1L);
                synchronized (reloadLock) {
                    pendingReloadTask = null;
                }
            });
        }
    }

    private void performReload() {
        ConfigSnapshot previous = currentSnapshot.get();
        ConfigSnapshot newSnapshot = buildSnapshot();

        if (newSnapshot.hasValidationErrors()) {
            if (previous != null && previous.isLoaded()) {
                previousSnapshot = previous;
                return;
            }
        }

        previousSnapshot = previous;
        currentSnapshot.set(newSnapshot);
    }

    private ConfigSnapshot buildSnapshot() {
        ValidationReport report = tierRepository.load();

        ConfigSnapshot.Builder builder = ConfigSnapshot.builder()
            .tiers(tierRepository.all())
            .validationReport(report);

        loadRootConfig(builder);
        loadMenus(builder);
        loadMessages(builder);
        loadStations(builder);

        return builder.build();
    }

    private void loadRootConfig(ConfigSnapshot.Builder builder) {
        org.bukkit.configuration.file.YamlConfiguration config =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.File(plugin.getDataFolder(), "config.yml")
            );

        for (String key : config.getKeys(true)) {
            if (!key.contains(".")) {
                Object value = config.get(key);
                if (value != null && !(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                    builder.putRoot(key, value);
                }
            }
        }
    }

    private void loadMenus(ConfigSnapshot.Builder builder) {
        java.io.File menusFile = new java.io.File(plugin.getDataFolder(), "menus.yml");
        if (!menusFile.exists()) {
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration menus =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(menusFile);

        for (String menuId : menus.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = menus.getConfigurationSection(menuId);
            if (section != null) {
                builder.putMenu(menuId, section.getValues(false));
            }
        }
    }

    private void loadMessages(ConfigSnapshot.Builder builder) {
        java.io.File messagesFile = new java.io.File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration messages =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);

        for (String messageId : messages.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = messages.getConfigurationSection(messageId);
            if (section != null) {
                builder.putMessage(messageId, section.getValues(false));
            }
        }
    }

    private void loadStations(ConfigSnapshot.Builder builder) {
        java.io.File stationsFile = new java.io.File(plugin.getDataFolder(), "stations.yml");
        if (!stationsFile.exists()) {
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration stations =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(stationsFile);

        for (String profileId : stations.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = stations.getConfigurationSection(profileId);
            if (section != null) {
                builder.putStationProfile(profileId, section.getValues(false));
            }
        }
    }

    public ConfigSnapshot getCurrentSnapshot() {
        ConfigSnapshot snapshot = currentSnapshot.get();
        return snapshot != null ? snapshot : ConfigSnapshot.builder().build();
    }

    public Optional<ConfigSnapshot> getPreviousSnapshot() {
        return Optional.ofNullable(previousSnapshot);
    }

    public TierRepository getTierRepository() {
        return tierRepository;
    }

    public Optional<TierDefinition> findTier(String id) {
        return tierRepository.find(id);
    }

    public List<TierDefinition> getAllTiers() {
        return tierRepository.all();
    }

    public TierDefinition createTier(String id, int priority) {
        TierDefinition tier = tierRepository.create(id, priority);
        updateSnapshotWithTierChange();
        return tier;
    }

    public TierDefinition cloneTier(String sourceId, String newId) {
        TierDefinition tier = tierRepository.clone(sourceId, newId);
        updateSnapshotWithTierChange();
        return tier;
    }

    public boolean deleteTier(String id) {
        boolean deleted = tierRepository.delete(id);
        if (deleted) {
            updateSnapshotWithTierChange();
        }
        return deleted;
    }

    private void updateSnapshotWithTierChange() {
        ConfigSnapshot current = currentSnapshot.get();
        if (current == null) {
            return;
        }

        ConfigSnapshot newSnapshot = ConfigSnapshot.builder()
            .putRootAll(current.getRootSettings())
            .putAuditAll(current.getAuditSettings())
            .tiers(tierRepository.all())
            .validationReport(current.getValidationReport())
            .build();

        currentSnapshot.set(newSnapshot);
    }

    public ValidationReport getValidationReport() {
        ConfigSnapshot snapshot = currentSnapshot.get();
        return snapshot != null ? snapshot.getValidationReport() : new ValidationReport();
    }

    public boolean hasValidationErrors() {
        ConfigSnapshot snapshot = currentSnapshot.get();
        return snapshot != null && snapshot.hasValidationErrors();
    }

    public boolean isLoaded() {
        ConfigSnapshot snapshot = currentSnapshot.get();
        return snapshot != null && snapshot.isLoaded();
    }

    public boolean hasPendingReload() {
        synchronized (reloadLock) {
            return pendingReloadTask != null;
        }
    }

    public void cancelPendingReload() {
        synchronized (reloadLock) {
            if (pendingReloadTask != null) {
                pendingReloadTask.cancel();
                pendingReloadTask = null;
            }
        }
    }
}
