package com.arkflame.flameforge.config;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigService {
    private static final class BuildCandidate {
        final ConfigSnapshot snapshot;
        final TierRepository.LoadCandidate tierCandidate;

        BuildCandidate(ConfigSnapshot snapshot, TierRepository.LoadCandidate tierCandidate) {
            this.snapshot = snapshot;
            this.tierCandidate = tierCandidate;
        }
    }

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

        BuildCandidate candidate = buildCandidate();
        if (candidate.snapshot.hasValidationErrors()) {
            throw new ConfigurationValidationException(
                "Initial load candidate has validation errors",
                candidate.snapshot.getValidationReport());
        }
        tierRepository.publish(candidate.tierCandidate);
        currentSnapshot.set(candidate.snapshot);
    }

    public CompletableFuture<Void> initialLoadAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            scheduler.runAsync(plugin, () -> {
                try {
                    tierRepository.bootstrapDefaultsIfDirectoryAbsent();
                    BuildCandidate candidate = buildCandidate();
                    if (candidate.snapshot.hasValidationErrors()) {
                        future.completeExceptionally(new ConfigurationValidationException(
                            "Initial load candidate has validation errors",
                            candidate.snapshot.getValidationReport()));
                        return;
                    }
                    tierRepository.publish(candidate.tierCandidate);
                    currentSnapshot.set(candidate.snapshot);
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

    private BuildCandidate buildCandidate() {
        TierRepository.LoadCandidate tierCandidate = tierRepository.loadCandidate();

        org.bukkit.configuration.file.YamlConfiguration candidateConfig = loadCandidateConfig();

        ValidationReport report = tierCandidate.getValidationReport();

        ConfigSnapshot.Builder builder = ConfigSnapshot.builder()
            .tiers(new java.util.ArrayList<>(tierCandidate.getByLevel().values()))
            .validationReport(report);

        loadBundledRootConfig(builder);
        overlayOperatorRootConfig(builder, candidateConfig);

        loadBundledMenus(builder);
        overlayOperatorMenus(builder);

        loadBundledMessages(builder);
        overlayOperatorMessages(builder);

        loadBundledStationProfiles(builder);
        overlayOperatorStationProfiles(builder);

        ConfigSnapshot snapshot = builder.build();
        validateMenuTree(snapshot, report);
        return new BuildCandidate(snapshot, tierCandidate);
    }

    private org.bukkit.configuration.file.YamlConfiguration loadCandidateConfig() {
        java.io.File operatorFile = new java.io.File(plugin.getDataFolder(), "config.yml");
        if (operatorFile.exists()) {
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(operatorFile);
        }
        InputStream stream = plugin.getResource("config.yml");
        if (stream == null) {
            throw new IllegalStateException("Bundled config.yml not found in plugin JAR");
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load bundled config.yml", e);
        }
    }

    private void putLeafValues(org.bukkit.configuration.file.YamlConfiguration configuration, ConfigSnapshot.Builder builder) {
        for (String key : configuration.getKeys(true)) {
            Object value = configuration.get(key);
            if (value != null && !(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                builder.putRoot(key, value);
            }
        }
    }

    private void loadBundledRootConfig(ConfigSnapshot.Builder builder) {
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
    }

    private void overlayOperatorRootConfig(ConfigSnapshot.Builder builder, org.bukkit.configuration.file.YamlConfiguration operatorConfig) {
        if (operatorConfig != null) {
            putLeafValues(operatorConfig, builder);
        }
    }

    private void loadBundledMenus(ConfigSnapshot.Builder builder) {
        try (InputStream stream = plugin.getResource("menus.yml")) {
            if (stream == null) {
                throw new IllegalStateException("Bundled menus.yml is required but not found in plugin JAR");
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                org.bukkit.configuration.file.YamlConfiguration bundledMenus =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
                for (String menuId : bundledMenus.getKeys(false)) {
                    org.bukkit.configuration.ConfigurationSection section = bundledMenus.getConfigurationSection(menuId);
                    if (section != null) {
                        builder.putMenu(menuId, section.getValues(false));
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load bundled menus.yml", e);
        }
    }

    private void overlayOperatorMenus(ConfigSnapshot.Builder builder) {
        java.io.File menusFile = new java.io.File(plugin.getDataFolder(), "menus.yml");
        if (!menusFile.exists()) {
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration menus =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(menusFile);

        for (String menuId : menus.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = menus.getConfigurationSection(menuId);
            if (section != null) {
                Map<String, Object> operatorValues = section.getValues(false);
                Map<String, Object> existing = getMenuFromBuilder(builder, menuId);
                Map<String, Object> merged = existing != null
                        ? recursiveMerge(existing, operatorValues)
                        : operatorValues;
                builder.mergeMenu(menuId, merged);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMenuFromBuilder(ConfigSnapshot.Builder builder, String menuId) {
        try {
            java.lang.reflect.Field field = ConfigSnapshot.Builder.class.getDeclaredField("menuSettings");
            field.setAccessible(true);
            Map<String, Map<String, Object>> menuSettings =
                    (Map<String, Map<String, Object>>) field.get(builder);
            Map<String, Object> menu = menuSettings.get(menuId);
            if (menu != null) {
                Map<String, Object> copy = new HashMap<>();
                for (Map.Entry<String, Object> entry : menu.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                        copy.put(entry.getKey(), normalizeConfigSection((org.bukkit.configuration.ConfigurationSection) value));
                    } else if (value instanceof Map) {
                        copy.put(entry.getKey(), new HashMap<>((Map<String, Object>) value));
                    } else {
                        copy.put(entry.getKey(), value);
                    }
                }
                return copy;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to access menuSettings field", e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> recursiveMerge(Map<String, Object> baseline, Map<String, Object> overlay) {
        Map<String, Object> result = new HashMap<>(baseline);
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            String key = entry.getKey();
            Object overlayValue = toMapOrList(entry.getValue());
            Object baselineValue = baseline.get(key);
            if (overlayValue == null) {
                continue;
            } else if (baselineValue instanceof Map && overlayValue instanceof Map) {
                result.put(key, recursiveMerge(
                        (Map<String, Object>) baselineValue,
                        (Map<String, Object>) overlayValue));
            } else if (overlayValue instanceof List) {
                result.put(key, normalizeList((List<?>) overlayValue));
            } else {
                result.put(key, overlayValue);
            }
        }
        return result;
    }

    private Object toMapOrList(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection) {
            org.bukkit.configuration.ConfigurationSection section =
                    (org.bukkit.configuration.ConfigurationSection) value;
            Map<String, Object> map = new HashMap<>();
            for (String subKey : section.getKeys(false)) {
                map.put(subKey, toMapOrList(section.get(subKey)));
            }
            return map;
        } else if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(toMapOrList(item));
            }
            return result;
        }
        return value;
    }

    private List<Object> normalizeList(List<?> list) {
        List<Object> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof org.bukkit.configuration.ConfigurationSection) {
                result.add(normalizeConfigSection((org.bukkit.configuration.ConfigurationSection) item));
            } else if (item instanceof Map) {
                result.add(new HashMap<>((Map<String, Object>) item));
            } else if (item instanceof List) {
                result.add(normalizeList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private Map<String, Object> normalizeConfigSection(org.bukkit.configuration.ConfigurationSection section) {
        Map<String, Object> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof org.bukkit.configuration.ConfigurationSection) {
                result.put(key, normalizeConfigSection((org.bukkit.configuration.ConfigurationSection) value));
            } else if (value instanceof List) {
                result.put(key, normalizeList((List<?>) value));
            } else if (value instanceof Map) {
                result.put(key, new HashMap<>((Map<String, Object>) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private void validateMenuTree(ConfigSnapshot snapshot, ValidationReport report) {
        for (String menuId : snapshot.getAllMenuSettings().keySet()) {
            Map<String, Object> menu = snapshot.getMenuSettings(menuId);
            validateMenuNode(menuId, menu, "", report);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateMenuNode(String menuId, Map<String, Object> node, String path, ValidationReport report) {
        if (node == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String childPath = path.isEmpty() ? key : path + "." + key;
            if (value instanceof Map) {
                validateMenuNode(menuId, (Map<String, Object>) value, childPath, report);
            } else if (value instanceof List) {
                for (int i = 0; i < ((List<?>) value).size(); i++) {
                    Object item = ((List<?>) value).get(i);
                    if (item instanceof Map) {
                        validateMenuNode(menuId, (Map<String, Object>) item, childPath + "[" + i + "]", report);
                    }
                }
            }
        }
    }

    private void loadBundledMessages(ConfigSnapshot.Builder builder) {
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream == null) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                org.bukkit.configuration.file.YamlConfiguration bundledMessages =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
                for (java.util.Map.Entry<String, java.util.Map<String, Object>> entry
                        : MessageTemplateLoader.flatten(bundledMessages).entrySet()) {
                    builder.putMessage(entry.getKey(), entry.getValue());
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load bundled messages.yml", e);
        }
    }

    private void overlayOperatorMessages(ConfigSnapshot.Builder builder) {
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

    private void loadBundledStationProfiles(ConfigSnapshot.Builder builder) {
        try (InputStream stream = plugin.getResource("station-profiles.yml")) {
            if (stream == null) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                org.bukkit.configuration.file.YamlConfiguration bundledProfiles =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
                for (String profileId : bundledProfiles.getKeys(false)) {
                    org.bukkit.configuration.ConfigurationSection section = bundledProfiles.getConfigurationSection(profileId);
                    if (section != null) {
                        builder.putStationProfile(profileId, section.getValues(false));
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load bundled station-profiles.yml", e);
        }
    }

    private void overlayOperatorStationProfiles(ConfigSnapshot.Builder builder) {
        java.io.File profilesFile = new java.io.File(plugin.getDataFolder(), "station-profiles.yml");
        if (!profilesFile.exists()) {
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration profiles =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(profilesFile);

        for (String profileId : profiles.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = profiles.getConfigurationSection(profileId);
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

        ConfigSnapshot.Builder builder = ConfigSnapshot.builder()
            .putRootAll(current.getRootSettings())
            .putAuditAll(current.getAuditSettings())
            .tiers(tierRepository.allAscending())
            .validationReport(current.getValidationReport());

        copyMapField(ConfigSnapshot.class, current, builder, "menuSettings");
        copyMapField(ConfigSnapshot.class, current, builder, "messageSettings");
        copyMapField(ConfigSnapshot.class, current, builder, "stationProfiles");
        copyMapField(ConfigSnapshot.class, current, builder, "itemGroups");
        copyMapField(ConfigSnapshot.class, current, builder, "catalysts");
        copyMapField(ConfigSnapshot.class, current, builder, "wards");
        copyMapField(ConfigSnapshot.class, current, builder, "announcements");

        currentSnapshot.set(builder.build());
    }

    @SuppressWarnings("unchecked")
    private void copyMapField(Class<?> clazz, Object source, ConfigSnapshot.Builder target, String fieldName) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(source);
            if (value instanceof java.util.Map) {
                java.util.Map<String, java.util.Map<String, Object>> map = (java.util.Map<String, java.util.Map<String, Object>>) value;
                java.util.Map<String, java.util.Map<String, Object>> targetMap = (java.util.Map<String, java.util.Map<String, Object>>) ConfigSnapshot.Builder.class.getDeclaredField(fieldName).get(target);
                targetMap.putAll(map);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to copy field: " + fieldName, e);
        }
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

            final CompletableFuture<ReloadResult> future = new CompletableFuture<>();

            try {
                pendingReloadTask = scheduler.runAsync(plugin, () -> {
                    try {
                        ReloadResult result = performReload();
                        future.complete(result);
                    } catch (Throwable failure) {
                        future.completeExceptionally(failure);
                    } finally {
                        synchronized (reloadLock) {
                            pendingReloadTask = null;
                        }
                    }
                });
            } catch (Throwable failure) {
                synchronized (reloadLock) {
                    pendingReloadTask = null;
                }
                return CompletableFuture.completedFuture(ReloadResult.schedulerRejected(reference));
            }

            return future;
        }
    }

    public CompletableFuture<ValidationResult> validateAsync() {
        synchronized (reloadLock) {
            if (pendingReloadTask != null) {
                return CompletableFuture.completedFuture(ValidationResult.alreadyRunning());
            }

            final int sequence = RELOAD_SEQUENCE.incrementAndGet();
            final String reference = "FF-VALIDATE-" + sequence;

            final CompletableFuture<ValidationResult> future = new CompletableFuture<>();

            try {
                scheduler.runAsync(plugin, () -> {
                    try {
                        BuildCandidate candidate = buildCandidate();
                        future.complete(ValidationResult.completed(candidate.snapshot.getValidationReport()));
                    } catch (Throwable failure) {
                        future.completeExceptionally(failure);
                    }
                });
            } catch (Throwable failure) {
                return CompletableFuture.completedFuture(ValidationResult.schedulerRejected(reference));
            }

            return future;
        }
    }

    private ReloadResult performReload() {
        ConfigSnapshot previous = currentSnapshot.get();
        try {
            BuildCandidate candidate = buildCandidate();

            if (candidate.snapshot.hasValidationErrors()) {
                previousSnapshot = previous;
                return ReloadResult.validationRejected(candidate.snapshot.getValidationReport());
            }

            tierRepository.publish(candidate.tierCandidate);
            previousSnapshot = previous;
            currentSnapshot.set(candidate.snapshot);
            return ReloadResult.applied(candidate.snapshot, candidate.snapshot.getValidationReport());
        } catch (Exception e) {
            return ReloadResult.loadFailed(e.getMessage(), "FF-RELOAD-" + RELOAD_SEQUENCE.get());
        }
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
