package com.arkflame.flameforge.config;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigService {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final TierRepository tierRepository;
    private final AtomicReference<ConfigSnapshot> currentSnapshot;
    private volatile ConfigSnapshot previousSnapshot;
    private final Object reloadLock = new Object();

    private volatile TaskHandle pendingReloadTask;

    public static final String LEGACY_V1_BACKUP_DIR = "tiers/.legacy-v1-backup";
    public static final String REPLACE_SCHEMA_V1_WITH_BUNDLED_V2_KEY = "replace-schema-v1-with-bundled-v2";

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

    public CompletableFuture<Void> initialLoadAsync() {
        try {
            tierRepository.bootstrapDefaultsIfDirectoryAbsent();
        } catch (Throwable failure) {
            return failedFuture(failure);
        }

        return scheduleAsync(() -> currentSnapshot.set(buildSnapshot()));
    }

    private CompletableFuture<Void> scheduleAsync(Runnable action) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            scheduler.runAsync(plugin, () -> {
                try {
                    action.run();
                    future.complete(null);
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable failure) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
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

    private ConfigSnapshot buildSnapshot() {
        boolean replaceSchemaV1 = getRootBoolean(REPLACE_SCHEMA_V1_WITH_BUNDLED_V2_KEY, false);
        ValidationReport report;
        if (replaceSchemaV1) {
            report = tierRepository.loadWithMigration(true);
        } else {
            report = tierRepository.load();
        }

        ConfigSnapshot.Builder builder = ConfigSnapshot.builder()
            .tiers(tierRepository.allAscending())
            .validationReport(report);

        loadRootConfig(builder);
        loadMenus(builder);
        loadMessages(builder);
        loadStations(builder);

        return builder.build();
    }

    private boolean getRootBoolean(String key, boolean def) {
        ConfigSnapshot snapshot = currentSnapshot.get();
        if (snapshot != null) {
            Object val = snapshot.getRootSetting(key);
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
        }
        return def;
    }

    private void putLeafValues(org.bukkit.configuration.file.YamlConfiguration configuration, ConfigSnapshot.Builder builder) {
        for (String key : configuration.getKeys(true)) {
            Object value = configuration.get(key);
            if (value != null && !(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                builder.putRoot(key, value);
            }
        }
    }

    private void loadRootConfig(ConfigSnapshot.Builder builder) {
        try (InputStream stream = plugin.getResource("config.yml");
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            if (stream == null) {
                throw new IllegalStateException("Bundled config.yml not found in plugin JAR");
            }

            org.bukkit.configuration.file.YamlConfiguration bundledConfig =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
            putLeafValues(bundledConfig, builder);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load bundled config.yml", e);
        }

        java.io.File operatorFile = new java.io.File(plugin.getDataFolder(), "config.yml");
        if (operatorFile.exists()) {
            org.bukkit.configuration.file.YamlConfiguration operatorConfig =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(operatorFile);
            putLeafValues(operatorConfig, builder);
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

        for (java.util.Map.Entry<String, java.util.Map<String, Object>> entry
                : MessageTemplateLoader.flatten(messages).entrySet()) {
            builder.putMessage(entry.getKey(), entry.getValue());
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
        return tierRepository.allAscending();
    }

    public TierDefinition createTier(String id, int priority) {
        TierDefinition tier = tierRepository.create(id, priority);
        updateSnapshotWithTierChange();
        return tier;
    }

    public TierDefinition cloneTier(String sourceId, String newId) {
        TierDefinition source = tierRepository.findById(sourceId).orElse(null);
        if (source == null) {
            return null;
        }
        int newLevel = source.getLevel();
        TierDefinition tier = tierRepository.clone(sourceId, newId, newLevel);
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
            .tiers(tierRepository.allAscending())
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

    private static final AtomicInteger RELOAD_SEQUENCE = new AtomicInteger(0);

    public CompletableFuture<ReloadResult> reloadAsync() {
        synchronized (reloadLock) {
            if (pendingReloadTask != null) {
                return CompletableFuture.completedFuture(ReloadResult.alreadyRunning());
            }

            final int sequence = RELOAD_SEQUENCE.incrementAndGet();
            final String reference = "FF-RELOAD-" + sequence;

            try {
                pendingReloadTask = scheduler.runAsync(plugin, () -> {
                    try {
                        performReload();
                        synchronized (reloadLock) {
                            pendingReloadTask = null;
                        }
                    } catch (Exception e) {
                        synchronized (reloadLock) {
                            pendingReloadTask = null;
                        }
                        throw e;
                    }
                });

                return CompletableFuture.supplyAsync(() -> {
                    return ReloadResult.applied(currentSnapshot.get(), currentSnapshot.get().getValidationReport());
                }, runnable -> scheduler.runAsync(plugin, runnable));

            } catch (Exception e) {
                return CompletableFuture.completedFuture(ReloadResult.loadFailed(e.getMessage(), reference));
            }
        }
    }

    public CompletableFuture<ValidationResult> validateAsync() {
        synchronized (reloadLock) {
            if (pendingReloadTask != null) {
                return CompletableFuture.completedFuture(ValidationResult.alreadyRunning());
            }

            final int sequence = RELOAD_SEQUENCE.incrementAndGet();
            final String reference = "FF-VALIDATE-" + sequence;

            try {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        ConfigSnapshot candidate = buildSnapshot();
                        return ValidationResult.completed(candidate.getValidationReport());
                    } catch (Exception e) {
                        return ValidationResult.loadFailed(e.getMessage(), reference);
                    }
                }, runnable -> scheduler.runAsync(plugin, runnable));

            } catch (Exception e) {
                return CompletableFuture.completedFuture(ValidationResult.schedulerRejected(reference));
            }
        }
    }

    private ReloadResult performReload() {
        ConfigSnapshot previous = currentSnapshot.get();
        ConfigSnapshot newSnapshot = buildSnapshot();

        if (newSnapshot.hasValidationErrors()) {
            if (previous != null && previous.isLoaded()) {
                previousSnapshot = previous;
                return ReloadResult.validationRejected(newSnapshot.getValidationReport());
            }
        }

        previousSnapshot = previous;
        currentSnapshot.set(newSnapshot);
        return ReloadResult.applied(newSnapshot, newSnapshot.getValidationReport());
    }

    public static final class ReloadResult {
        public enum Status {
            APPLIED,
            VALIDATION_REJECTED,
            ALREADY_RUNNING,
            SCHEDULER_REJECTED,
            LOAD_FAILED
        }

        private final Status status;
        private final ValidationReport validationReport;
        private final String reason;
        private final String reference;
        private final ConfigSnapshot appliedSnapshot;

        private ReloadResult(Status status, ValidationReport validationReport, String reason, String reference, ConfigSnapshot appliedSnapshot) {
            this.status = status;
            this.validationReport = validationReport;
            this.reason = reason;
            this.reference = reference;
            this.appliedSnapshot = appliedSnapshot;
        }

        public Status getStatus() { return status; }
        public ValidationReport getValidationReport() { return validationReport; }
        public String getReason() { return reason; }
        public String getReference() { return reference; }
        public ConfigSnapshot getAppliedSnapshot() { return appliedSnapshot; }

        public static ReloadResult applied(ConfigSnapshot snapshot, ValidationReport report) {
            return new ReloadResult(Status.APPLIED, report, null, null, snapshot);
        }
        public static ReloadResult validationRejected(ValidationReport report) {
            return new ReloadResult(Status.VALIDATION_REJECTED, report, null, null, null);
        }
        public static ReloadResult alreadyRunning() {
            return new ReloadResult(Status.ALREADY_RUNNING, null, null, null, null);
        }
        public static ReloadResult schedulerRejected(String reference) {
            return new ReloadResult(Status.SCHEDULER_REJECTED, null, null, reference, null);
        }
        public static ReloadResult loadFailed(String reason, String reference) {
            return new ReloadResult(Status.LOAD_FAILED, null, reason, reference, null);
        }
    }

    public static final class ValidationResult {
        public enum Status {
            COMPLETED,
            ALREADY_RUNNING,
            SCHEDULER_REJECTED,
            LOAD_FAILED
        }

        private final Status status;
        private final ValidationReport validationReport;
        private final String reason;
        private final String reference;

        private ValidationResult(Status status, ValidationReport validationReport, String reason, String reference) {
            this.status = status;
            this.validationReport = validationReport;
            this.reason = reason;
            this.reference = reference;
        }

        public Status getStatus() { return status; }
        public ValidationReport getValidationReport() { return validationReport; }
        public String getReason() { return reason; }
        public String getReference() { return reference; }

        public static ValidationResult completed(ValidationReport report) {
            return new ValidationResult(Status.COMPLETED, report, null, null);
        }
        public static ValidationResult alreadyRunning() {
            return new ValidationResult(Status.ALREADY_RUNNING, null, null, null);
        }
        public static ValidationResult schedulerRejected(String reference) {
            return new ValidationResult(Status.SCHEDULER_REJECTED, null, null, reference);
        }
        public static ValidationResult loadFailed(String reason, String reference) {
            return new ValidationResult(Status.LOAD_FAILED, null, reason, reference);
        }
    }
}
