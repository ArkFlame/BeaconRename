package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.station.StationIdPolicy;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class StationRepository {
    private static final String STATIONS_DIR = "stations";
    private static final String FILE_PREFIX = ".";
    private static final String FILE_SUFFIX_TMP = ".yml.tmp";
    private static final String FILE_SUFFIX_DELETE = ".yml.delete";
    private static final int MAX_REFERENCE_LENGTH = 48;
    private static final Pattern VALID_REFERENCE_CHARS = Pattern.compile("[^A-Z0-9]");
    private static final Pattern COLLAPSE_DASHES = Pattern.compile("-+");

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final Path dataFolder;
    private volatile StationIndex currentIndex = StationIndex.empty();
    private volatile StationLoadReport lastLoadReport;
    private final Object mutationLock = new Object();

    public StationRepository(JavaPlugin plugin, SchedulerBridge scheduler, Path dataFolder) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.dataFolder = dataFolder;
    }

    public CompletableFuture<StationLoadReport> loadAsync() {
        CompletableFuture<StationLoadReport> future = new CompletableFuture<>();
        try {
            scheduler.runAsync(plugin, () -> {
                try {
                    StationLoadReport result = loadFromDisk();
                    future.complete(result);
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private StationLoadReport loadFromDisk() {
        Path stationsDir = dataFolder.resolve(STATIONS_DIR);
        Map<String, RegisteredForge> loadedById = new java.util.LinkedHashMap<>();
        Map<String, String> loadedIdByLocation = new java.util.LinkedHashMap<>();
        List<StationFileIssue> issues = new ArrayList<>();

        try {
            Files.createDirectories(stationsDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create stations directory: " + e.getMessage(), e);
        }

        List<Path> stationFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stationsDir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry) && entry.toString().endsWith(".yml") && !Files.isSymbolicLink(entry)) {
                    stationFiles.add(entry);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to enumerate station files: " + e.getMessage(), e);
        }

        stationFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));

        for (Path file : stationFiles) {
            String filename = file.getFileName().toString();
            String id = filename.substring(0, filename.length() - 4);
            String ref = sanitizeReference(id, "FF-STATION-FILE-");

            if (!StationIdPolicy.isValidExplicit(id)) {
                issues.add(new StationFileIssue(StationFileIssueType.INVALID_FILENAME, filename,
                    "Invalid station ID format", ref));
                plugin.getLogger().warning("Skipped station file " + STATIONS_DIR + "/" + filename + ": Invalid station ID format (reference: " + ref + ")");
                continue;
            }

            String normalizedId = StationIdPolicy.normalize(id);

            org.bukkit.configuration.file.YamlConfiguration yaml;
            try {
                yaml = new org.bukkit.configuration.file.YamlConfiguration();
                yaml.load(file.toFile());
            } catch (Exception e) {
                issues.add(new StationFileIssue(StationFileIssueType.MALFORMED_YAML, filename,
                    "Failed to parse YAML: " + e.getMessage(), ref));
                plugin.getLogger().warning("Skipped station file " + STATIONS_DIR + "/" + filename + ": Malformed YAML (reference: " + ref + ")");
                continue;
            }

            if (loadedById.containsKey(normalizedId)) {
                issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                    "Duplicate station ID in load", ref));
                plugin.getLogger().warning("Skipped station file " + STATIONS_DIR + "/" + filename + ": Duplicate station ID in load (reference: " + ref + ")");
                continue;
            }

            ParsedStation parsed = parseAndValidate(yaml, filename, ref, issues);
            if (parsed == null) {
                issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                    "Schema validation failed", ref));
                continue;
            }

            String locationKey = locationKey(parsed.worldName, parsed.x, parsed.y, parsed.z);
            if (loadedIdByLocation.containsKey(locationKey)) {
                String existingId = loadedIdByLocation.get(locationKey);
                issues.add(new StationFileIssue(StationFileIssueType.DUPLICATE_LOCATION, filename,
                    "Duplicate location with station " + existingId, ref));
                plugin.getLogger().warning("Skipped station file " + STATIONS_DIR + "/" + filename + ": Duplicate location (reference: " + ref + ")");
                continue;
            }

            RegisteredForge forge = new RegisteredForge(normalizedId, parsed.worldUuid, parsed.worldName,
                parsed.x, parsed.y, parsed.z, parsed.profileId);
            loadedById.put(normalizedId, forge);
            loadedIdByLocation.put(locationKey, normalizedId);
        }

        StationIndex newIndex = StationIndex.of(loadedById, loadedIdByLocation);
        this.currentIndex = newIndex;

        int loadedCount = loadedById.size();
        StationLoadReport report = new StationLoadReport(loadedCount, issues);
        this.lastLoadReport = report;
        return report;
    }

    private ParsedStation parseAndValidate(org.bukkit.configuration.file.YamlConfiguration config,
            String filename, String ref, List<StationFileIssue> issues) {
        if (!config.contains("schema-version")) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing schema-version", ref));
            return null;
        }
        int schemaVersion = config.getInt("schema-version");
        if (schemaVersion != 1) {
            issues.add(new StationFileIssue(StationFileIssueType.UNSUPPORTED_SCHEMA, filename,
                "Unsupported schema version: " + schemaVersion, ref));
            return null;
        }

        if (!config.contains("world") || !(config.get("world") instanceof org.bukkit.configuration.ConfigurationSection)) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing or invalid world section", ref));
            return null;
        }
        org.bukkit.configuration.ConfigurationSection worldSection = config.getConfigurationSection("world");

        if (!worldSection.contains("name") || worldSection.get("name") == null) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing world.name", ref));
            return null;
        }
        String worldName = worldSection.getString("name");
        if (worldName == null || worldName.trim().isEmpty()) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "world.name is blank", ref));
            return null;
        }

        if (!worldSection.contains("uuid") || worldSection.get("uuid") == null) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing world.uuid", ref));
            return null;
        }
        String uuidStr = worldSection.getString("uuid");
        UUID worldUuid;
        try {
            worldUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Invalid UUID format: " + uuidStr, ref));
            return null;
        }

        if (!config.contains("location") || !(config.get("location") instanceof org.bukkit.configuration.ConfigurationSection)) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing or invalid location section", ref));
            return null;
        }
        org.bukkit.configuration.ConfigurationSection locationSection = config.getConfigurationSection("location");

        if (!locationSection.contains("x")) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing location.x", ref));
            return null;
        }
        if (!locationSection.contains("y")) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing location.y", ref));
            return null;
        }
        if (!locationSection.contains("z")) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing location.z", ref));
            return null;
        }

        double rawX = locationSection.getDouble("x");
        double rawY = locationSection.getDouble("y");
        double rawZ = locationSection.getDouble("z");

        if (rawX != Math.floor(rawX) || rawY != Math.floor(rawY) || rawZ != Math.floor(rawZ)) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Coordinates must be integers", ref));
            return null;
        }

        int x = (int) rawX;
        int y = (int) rawY;
        int z = (int) rawZ;

        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE ||
            y < Integer.MIN_VALUE || y > Integer.MAX_VALUE ||
            z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Coordinates out of int range", ref));
            return null;
        }

        if (!config.contains("profile") || config.get("profile") == null) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "Missing profile", ref));
            return null;
        }
        String profileId = config.getString("profile");
        if (profileId == null || profileId.trim().isEmpty()) {
            issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                "profile is blank", ref));
            return null;
        }

        for (String key : config.getKeys(false)) {
            if (!key.equals("schema-version") && !key.equals("world") && !key.equals("location") && !key.equals("profile")) {
                issues.add(new StationFileIssue(StationFileIssueType.INVALID_FIELD, filename,
                    "Unknown root key: " + key, ref));
                return null;
            }
        }

        return new ParsedStation(worldName, worldUuid, x, y, z, profileId);
    }

    private CompletableFuture<Void> scheduleAsync(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
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

    private <T> CompletableFuture<T> submitAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            scheduler.runAsync(plugin, () -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public CompletableFuture<AddOutcome> addAndSave(RegisteredForge forge) {
        return CompletableFuture.supplyAsync(() -> {
            String normalizedId = StationIdPolicy.normalize(forge.id);
            if (!StationIdPolicy.isValidExplicit(normalizedId)) {
                return new AddOutcome(AddResult.INVALID_ID, null, makeWriteRef(normalizedId, "FF-STATION-WRITE-"));
            }

            StationIndex snapshot = this.currentIndex;
            if (snapshot.byNormalizedId.containsKey(normalizedId)) {
                return new AddOutcome(AddResult.DUPLICATE_ID, null, makeWriteRef(normalizedId, "FF-STATION-WRITE-"));
            }

            String locationKey = locationKey(forge.worldName, forge.x, forge.y, forge.z);
            if (snapshot.idByLocation.containsKey(locationKey)) {
                return new AddOutcome(AddResult.DUPLICATE_LOCATION, null, makeWriteRef(normalizedId, "FF-STATION-WRITE-"));
            }

            RegisteredForge immutableForge = new RegisteredForge(
                normalizedId,
                forge.worldUuid,
                forge.worldName,
                forge.x,
                forge.y,
                forge.z,
                forge.profileId
            );

            Path stationsDir = dataFolder.resolve(STATIONS_DIR);
            Path targetFile = stationsDir.resolve(normalizedId + ".yml");

            if (Files.exists(targetFile) && !snapshot.byNormalizedId.containsKey(normalizedId)) {
                return new AddOutcome(AddResult.STORAGE_CONFLICT, null,
                    makeConflictRef(normalizedId, "FF-STATION-CONFLICT-"));
            }

            Path tempFile = stationsDir.resolve(FILE_PREFIX + normalizedId + FILE_SUFFIX_TMP);

            try {
                Files.createDirectories(stationsDir);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create stations directory: " + e.getMessage());
                return new AddOutcome(AddResult.PERSISTENCE_FAILED, null, makeWriteRef(normalizedId, "FF-STATION-WRITE-"));
            }

            try {
                org.bukkit.configuration.file.YamlConfiguration config =
                    new org.bukkit.configuration.file.YamlConfiguration();

                config.set("schema-version", 1);
                config.set("world.name", immutableForge.worldName);
                config.set("world.uuid", immutableForge.worldUuid.toString());
                config.set("location.x", immutableForge.x);
                config.set("location.y", immutableForge.y);
                config.set("location.z", immutableForge.z);
                config.set("profile", immutableForge.profileId);

                config.save(tempFile.toFile());

                moveReplacing(tempFile, targetFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save station file: " + e.getMessage());
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
                return new AddOutcome(AddResult.PERSISTENCE_FAILED, null, makeWriteRef(normalizedId, "FF-STATION-WRITE-"));
            }

            Map<String, RegisteredForge> newById;
            Map<String, String> newIdByLoc;
            synchronized (mutationLock) {
                newById = new java.util.LinkedHashMap<>(snapshot.byNormalizedId);
                newIdByLoc = new java.util.LinkedHashMap<>(snapshot.idByLocation);
                newById.put(normalizedId, immutableForge);
                newIdByLoc.put(locationKey, normalizedId);

                this.currentIndex = StationIndex.of(newById, newIdByLoc);
            }

            return new AddOutcome(AddResult.ADDED, immutableForge, null);
        }, runnable -> scheduler.runAsync(plugin, runnable));
    }

    public CompletableFuture<RemoveOutcome> removeAndSave(String normalizedId) {
        final String id = StationIdPolicy.normalize(normalizedId);
        return CompletableFuture.supplyAsync(() -> {
            StationIndex snapshot = this.currentIndex;
            RegisteredForge removed = snapshot.byNormalizedId.get(id);
            if (removed == null) {
                return new RemoveOutcome(Result.NOT_FOUND, null, null);
            }

            String locationKey = locationKey(removed.worldName, removed.x, removed.y, removed.z);

            Path stationsDir = dataFolder.resolve(STATIONS_DIR);
            Path targetFile = stationsDir.resolve(id + ".yml");
            Path tombstoneFile = stationsDir.resolve(id + FILE_SUFFIX_DELETE);

            try {
                if (Files.exists(targetFile)) {
                    moveReplacing(targetFile, tombstoneFile);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to move station file to tombstone: " + e.getMessage());
                return new RemoveOutcome(Result.PERSISTENCE_FAILED, null, makeDeleteRef(id, "FF-STATION-DELETE-"));
            }

            Map<String, RegisteredForge> newById;
            Map<String, String> newIdByLoc;
            synchronized (mutationLock) {
                newById = new java.util.LinkedHashMap<>(snapshot.byNormalizedId);
                newIdByLoc = new java.util.LinkedHashMap<>(snapshot.idByLocation);
                newById.remove(id);
                newIdByLoc.remove(locationKey);

                this.currentIndex = StationIndex.of(newById, newIdByLoc);
            }

            try {
                Files.deleteIfExists(tombstoneFile);
            } catch (IOException ignored) {}

            return new RemoveOutcome(Result.REMOVED, removed, null);
        }, runnable -> scheduler.runAsync(plugin, runnable));
    }

    private static String sanitizeReference(String input, String prefix) {
        if (input == null || input.isEmpty()) {
            return prefix + "UNKNOWN";
        }
        String upper = input.toUpperCase(Locale.ROOT);
        String sanitized = VALID_REFERENCE_CHARS.matcher(upper).replaceAll("-");
        sanitized = COLLAPSE_DASHES.matcher(sanitized).replaceAll("-");
        sanitized = sanitized.replaceAll("^-|-$", "");
        if (sanitized.isEmpty()) {
            sanitized = "UNKNOWN";
        }
        if (sanitized.length() > MAX_REFERENCE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_REFERENCE_LENGTH);
        }
        String result = prefix + sanitized;
        if (result.length() > MAX_REFERENCE_LENGTH + prefix.length()) {
            return prefix + sanitized.substring(0, MAX_REFERENCE_LENGTH);
        }
        return result;
    }

    private static String makeWriteRef(String id, String prefix) {
        return sanitizeReference(id, prefix);
    }

    private static String makeConflictRef(String id, String prefix) {
        return sanitizeReference(id, prefix);
    }

    private static String makeDeleteRef(String id, String prefix) {
        return sanitizeReference(id, prefix);
    }

    public Optional<RegisteredForge> findById(String id) {
        StationIndex index = this.currentIndex;
        return Optional.ofNullable(index.byNormalizedId.get(StationIdPolicy.normalize(id)));
    }

    public Optional<RegisteredForge> findByKey(String worldName, int x, int y, int z) {
        StationIndex index = this.currentIndex;
        String id = index.idByLocation.get(locationKey(worldName, x, y, z));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index.byNormalizedId.get(id));
    }

    public Optional<RegisteredForge> findByKey(StationKey key) {
        StationIndex index = this.currentIndex;
        String id = index.idByLocation.get(locationKey(key.worldName, key.x, key.y, key.z));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index.byNormalizedId.get(id));
    }

    public List<RegisteredForge> snapshotSortedById() {
        StationIndex index = this.currentIndex;
        List<RegisteredForge> result = new ArrayList<>(index.byNormalizedId.values());
        Collections.sort(result, (a, b) -> a.id.compareToIgnoreCase(b.id));
        return Collections.unmodifiableList(result);
    }

    public List<String> snapshotIds() {
        return snapshotSortedById().stream()
            .map(RegisteredForge::getId)
            .collect(java.util.stream.Collectors.toList());
    }

    public List<RegisteredForge> getAllSnapshot() {
        return snapshotSortedById();
    }

    public int size() {
        return this.currentIndex.byNormalizedId.size();
    }

    public StationLoadReport getLastLoadReport() {
        return lastLoadReport;
    }

    private static String locationKey(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    private static final class StationIndex {
        final Map<String, RegisteredForge> byNormalizedId;
        final Map<String, String> idByLocation;

        private StationIndex(Map<String, RegisteredForge> byNormalizedId, Map<String, String> idByLocation) {
            this.byNormalizedId = byNormalizedId;
            this.idByLocation = idByLocation;
        }

        static StationIndex empty() {
            return new StationIndex(
                Collections.unmodifiableMap(Collections.emptyMap()),
                Collections.unmodifiableMap(Collections.emptyMap())
            );
        }

        static StationIndex of(Map<String, RegisteredForge> byId, Map<String, String> idByLoc) {
            return new StationIndex(
                Collections.unmodifiableMap(new java.util.LinkedHashMap(byId)),
                Collections.unmodifiableMap(new java.util.LinkedHashMap(idByLoc))
            );
        }
    }

    public static final class StationLoadReport {
        private final int loadedCount;
        private final List<StationFileIssue> issues;

        public StationLoadReport(int loadedCount, List<StationFileIssue> issues) {
            this.loadedCount = loadedCount;
            this.issues = issues != null ? new ArrayList<>(issues) : Collections.emptyList();
        }

        public int getLoadedCount() { return loadedCount; }
        public int getSkippedCount() { return issues.size(); }
        public List<StationFileIssue> getIssues() { return Collections.unmodifiableList(issues); }
    }

    public enum StationFileIssueType {
        INVALID_FILENAME,
        MALFORMED_YAML,
        UNSUPPORTED_SCHEMA,
        INVALID_FIELD,
        DUPLICATE_LOCATION,
        UNSUPPORTED_FILE
    }

    public static final class StationFileIssue {
        private final StationFileIssueType type;
        private final String relativePath;
        private final String reason;
        private final String reference;

        public StationFileIssue(StationFileIssueType type, String relativePath, String reason, String reference) {
            this.type = type;
            this.relativePath = relativePath;
            this.reason = reason;
            this.reference = reference;
        }

        public StationFileIssueType getType() { return type; }
        public String getRelativePath() { return relativePath; }
        public String getReason() { return reason; }
        public String getReference() { return reference; }
    }

    public static final class RegisteredForge {
        private final String id;
        private final UUID worldUuid;
        private final String worldName;
        private final int x, y, z;
        private final String profileId;

        public RegisteredForge(String id, UUID worldUuid, String worldName, int x, int y, int z, String profileId) {
            this.id = Objects.requireNonNull(id);
            this.worldUuid = worldUuid;
            this.worldName = Objects.requireNonNull(worldName);
            this.x = x;
            this.y = y;
            this.z = z;
            this.profileId = profileId != null ? profileId : "default";
        }

        public String getId() { return id; }
        public UUID getWorldUuid() { return worldUuid; }
        public String getWorldName() { return worldName; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public String getProfileId() { return profileId; }

        public StationKey toStationKey() {
            return new StationKey(worldUuid, worldName, x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RegisteredForge)) return false;
            RegisteredForge that = (RegisteredForge) o;
            return that.id.equals(id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    public static final class StationKey {
        private final UUID worldUuid;
        private final String worldName;
        private final int x, y, z;

        public StationKey(UUID worldUuid, String worldName, int x, int y, int z) {
            this.worldUuid = worldUuid;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public UUID getWorldUuid() { return worldUuid; }
        public String getWorldName() { return worldName; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof StationKey)) return false;
            StationKey that = (StationKey) o;
            if (worldUuid != null && that.worldUuid != null) {
                return worldUuid.equals(that.worldUuid) && that.x == x && that.y == y && that.z == z;
            }
            return Objects.equals(worldName, that.worldName) && that.x == x && that.y == y && that.z == z;
        }

        @Override
        public int hashCode() {
            if (worldUuid != null) {
                return Objects.hash(worldUuid, x, y, z);
            }
            return Objects.hash(worldName, x, y, z);
        }

        @Override
        public String toString() {
            return (worldName != null ? worldName : worldUuid) + "_" + x + "_" + y + "_" + z;
        }
    }

    public enum AddResult {
        ADDED,
        INVALID_ID,
        DUPLICATE_ID,
        DUPLICATE_LOCATION,
        STORAGE_CONFLICT,
        PERSISTENCE_FAILED
    }

    public enum Result {
        REMOVED,
        NOT_FOUND,
        PERSISTENCE_FAILED
    }

    public static final class AddOutcome {
        private final AddResult result;
        private final RegisteredForge addedForge;
        private final String reference;

        private AddOutcome(AddResult result, RegisteredForge addedForge, String reference) {
            this.result = result;
            this.addedForge = addedForge;
            this.reference = reference;
        }

        public static AddOutcome added(RegisteredForge forge) {
            return new AddOutcome(AddResult.ADDED, forge, null);
        }

        public static AddOutcome invalidId(String ref) {
            return new AddOutcome(AddResult.INVALID_ID, null, ref);
        }

        public static AddOutcome duplicateId(String ref) {
            return new AddOutcome(AddResult.DUPLICATE_ID, null, ref);
        }

        public static AddOutcome duplicateLocation(String ref) {
            return new AddOutcome(AddResult.DUPLICATE_LOCATION, null, ref);
        }

        public static AddOutcome storageConflict(String ref) {
            return new AddOutcome(AddResult.STORAGE_CONFLICT, null, ref);
        }

        public static AddOutcome persistenceFailed(RegisteredForge forge) {
            return new AddOutcome(AddResult.PERSISTENCE_FAILED, forge, null);
        }

        public AddResult getResult() { return result; }
        public RegisteredForge getAddedForge() { return addedForge; }
        public String getReference() { return reference; }
    }

    public static final class RemoveOutcome {
        private final Result result;
        private final RegisteredForge removedForge;
        private final String reference;

        private RemoveOutcome(Result result, RegisteredForge removedForge, String reference) {
            this.result = result;
            this.removedForge = removedForge;
            this.reference = reference;
        }

        public static RemoveOutcome removed(RegisteredForge forge) {
            return new RemoveOutcome(Result.REMOVED, forge, null);
        }

        public static RemoveOutcome notFound(RegisteredForge forge) {
            return new RemoveOutcome(Result.NOT_FOUND, forge, null);
        }

        public static RemoveOutcome persistenceFailed(RegisteredForge forge) {
            return new RemoveOutcome(Result.PERSISTENCE_FAILED, forge, null);
        }

        public Result getResult() { return result; }
        public RegisteredForge getRemovedForge() { return removedForge; }
        public String getReference() { return reference; }
    }

    public static final class StationData {
        public final String id;
        public final String world;
        public final int x, y, z;
        public final String profile;

        public StationData(String id, String world, int x, int y, int z, String profile) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.profile = profile;
        }

        public Location toLocation(org.bukkit.World bukkitWorld) {
            return new Location(bukkitWorld, x, y, z);
        }
    }

    private static final class ParsedStation {
        final String worldName;
        final UUID worldUuid;
        final int x, y, z;
        final String profileId;

        ParsedStation(String worldName, UUID worldUuid, int x, int y, int z, String profileId) {
            this.worldName = worldName;
            this.worldUuid = worldUuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.profileId = profileId;
        }
    }
}
