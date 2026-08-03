package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class StationRepository {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final Path dataFolder;
    private final Path stationsFile;
    private final ConcurrentHashMap<String, RegisteredForge> byNormalizedId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idByLocation = new ConcurrentHashMap<>();

    public StationRepository(JavaPlugin plugin, SchedulerBridge scheduler, Path dataFolder) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.dataFolder = dataFolder;
        this.stationsFile = dataFolder.resolve("stations.yml");
    }

    public void load() {
        try {
            loadFromDisk();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load stations: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> loadAsync() {
        return scheduleAsync(this::loadFromDisk);
    }

    private void loadFromDisk() {
        Path file = stationsFile;
        Map<String, RegisteredForge> loadedById = new HashMap<>();
        Map<String, String> loadedIdByLocation = new HashMap<>();
        if (!Files.exists(file)) {
            byNormalizedId.clear();
            idByLocation.clear();
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration config =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());

        for (String key : config.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            String rawId = section.getString("id", key);
            String normalizedId = normalizeId(rawId);

            if (loadedById.containsKey(normalizedId)) {
                plugin.getLogger().warning("Duplicate station ID detected: " + rawId + ", skipping");
                continue;
            }

            String worldName = section.getString("world", "");
            double rawX = section.getDouble("x", 0);
            double rawY = section.getDouble("y", 0);
            double rawZ = section.getDouble("z", 0);
            int x = (int) Math.floor(rawX);
            int y = (int) Math.floor(rawY);
            int z = (int) Math.floor(rawZ);
            String profileId = section.getString("profile", "default");

            String locationKey = locationKey(worldName, x, y, z);
            if (loadedIdByLocation.containsKey(locationKey)) {
                plugin.getLogger().warning("Duplicate station location: " + locationKey + ", skipping ID: " + rawId);
                continue;
            }

            UUID worldUuid = parseWorldUuid(section);
            RegisteredForge forge = new RegisteredForge(normalizedId, worldUuid, worldName, x, y, z, profileId);
            loadedById.put(normalizedId, forge);
            loadedIdByLocation.put(locationKey, normalizedId);
        }

        byNormalizedId.clear();
        idByLocation.clear();
        byNormalizedId.putAll(loadedById);
        idByLocation.putAll(loadedIdByLocation);
    }

    private static UUID parseWorldUuid(org.bukkit.configuration.ConfigurationSection section) {
        String rawUuid = section.getString("worldUuid", null);
        if (rawUuid == null) {
            rawUuid = section.getString("world-uuid", null);
        }
        if (rawUuid == null || rawUuid.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    public CompletableFuture<AddOutcome> addAndSave(RegisteredForge forge) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                String normalizedId = normalizeId(forge.id);
                if (byNormalizedId.containsKey(normalizedId)) {
                    return AddOutcome.duplicateId(null);
                }
                String locationKey = locationKey(forge.worldName, forge.x, forge.y, forge.z);
                if (idByLocation.containsKey(locationKey)) {
                    return AddOutcome.duplicateLocation(null);
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

                byNormalizedId.put(normalizedId, immutableForge);
                idByLocation.put(locationKey, normalizedId);

                Path tempFile = null;
                try {
                    Files.createDirectories(dataFolder);
                    tempFile = dataFolder.resolve("stations.yml.tmp");

                    org.bukkit.configuration.file.YamlConfiguration config =
                        new org.bukkit.configuration.file.YamlConfiguration();

                    for (RegisteredForge f : byNormalizedId.values()) {
                        String sectionKey = f.id;
                        org.bukkit.configuration.ConfigurationSection section = config.createSection(sectionKey);
                        section.set("id", f.id);
                        section.set("world", f.worldName);
                        if (f.worldUuid != null) {
                            section.set("worldUuid", f.worldUuid.toString());
                        }
                        section.set("x", f.x);
                        section.set("y", f.y);
                        section.set("z", f.z);
                        section.set("profile", f.profileId);
                    }

                    config.save(tempFile.toFile());
                    Files.move(tempFile, stationsFile, StandardCopyOption.REPLACE_EXISTING);

                    return AddOutcome.added(immutableForge);
                } catch (Exception e) {
                    byNormalizedId.remove(normalizedId);
                    idByLocation.remove(locationKey);

                    if (tempFile != null && Files.exists(tempFile)) {
                        try {
                            Files.delete(tempFile);
                        } catch (IOException ignored) {
                        }
                    }
                    plugin.getLogger().severe("Failed to save station: " + e.getMessage());
                    return AddOutcome.persistenceFailed(null);
                }
            }
        }, runnable -> scheduler.runAsync(plugin, runnable));
    }

    public CompletableFuture<RemoveOutcome> removeAndSave(String normalizedId) {
        final String normalized = normalizeId(normalizedId);
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                RegisteredForge removed = byNormalizedId.get(normalized);
                if (removed == null) {
                    return RemoveOutcome.notFound(null);
                }
                String locationKey = locationKey(removed.worldName, removed.x, removed.y, removed.z);

                byNormalizedId.remove(normalized);
                idByLocation.remove(locationKey);

                Path tempFile = null;
                try {
                    Files.createDirectories(dataFolder);
                    tempFile = dataFolder.resolve("stations.yml.tmp");

                    org.bukkit.configuration.file.YamlConfiguration config =
                        new org.bukkit.configuration.file.YamlConfiguration();

                    for (RegisteredForge f : byNormalizedId.values()) {
                        String sectionKey = f.id;
                        org.bukkit.configuration.ConfigurationSection section = config.createSection(sectionKey);
                        section.set("id", f.id);
                        section.set("world", f.worldName);
                        if (f.worldUuid != null) {
                            section.set("worldUuid", f.worldUuid.toString());
                        }
                        section.set("x", f.x);
                        section.set("y", f.y);
                        section.set("z", f.z);
                        section.set("profile", f.profileId);
                    }

                    config.save(tempFile.toFile());
                    Files.move(tempFile, stationsFile, StandardCopyOption.REPLACE_EXISTING);

                    return RemoveOutcome.removed(removed);
                } catch (Exception e) {
                    byNormalizedId.put(normalized, removed);
                    idByLocation.put(locationKey, normalized);

                    if (tempFile != null && Files.exists(tempFile)) {
                        try {
                            Files.delete(tempFile);
                        } catch (IOException ignored) {
                        }
                    }
                    plugin.getLogger().severe("Failed to save after removal: " + e.getMessage());
                    return RemoveOutcome.persistenceFailed(null);
                }
            }
        }, runnable -> scheduler.runAsync(plugin, runnable));
    }

    public Optional<RegisteredForge> findById(String id) {
        return Optional.ofNullable(byNormalizedId.get(normalizeId(id)));
    }

    public Optional<RegisteredForge> findByKey(String worldName, int x, int y, int z) {
        String id = idByLocation.get(locationKey(worldName, x, y, z));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNormalizedId.get(id));
    }

    public Optional<RegisteredForge> findByKey(StationKey key) {
        String id = idByLocation.get(locationKey(key.worldName, key.x, key.y, key.z));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byNormalizedId.get(id));
    }

    public List<RegisteredForge> snapshotSortedById() {
        List<RegisteredForge> result = byNormalizedId.values().stream()
            .sorted((a, b) -> a.id.compareToIgnoreCase(b.id))
            .collect(Collectors.toList());
        return Collections.unmodifiableList(result);
    }

    public List<String> snapshotIds() {
        return snapshotSortedById().stream()
            .map(RegisteredForge::getId)
            .collect(Collectors.toList());
    }

    public void flush() {
    }

    public List<RegisteredForge> getAllSnapshot() {
        return snapshotSortedById();
    }

    public int size() {
        return byNormalizedId.size();
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
    }

    private static String locationKey(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
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
        DUPLICATE_ID,
        DUPLICATE_LOCATION,
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

        private AddOutcome(AddResult result, RegisteredForge addedForge) {
            this.result = result;
            this.addedForge = addedForge;
        }

        public static AddOutcome added(RegisteredForge forge) {
            return new AddOutcome(AddResult.ADDED, forge);
        }

        public static AddOutcome duplicateId(RegisteredForge forge) {
            return new AddOutcome(AddResult.DUPLICATE_ID, forge);
        }

        public static AddOutcome duplicateLocation(RegisteredForge forge) {
            return new AddOutcome(AddResult.DUPLICATE_LOCATION, forge);
        }

        public static AddOutcome persistenceFailed(RegisteredForge forge) {
            return new AddOutcome(AddResult.PERSISTENCE_FAILED, forge);
        }

        public AddResult getResult() { return result; }
        public RegisteredForge getAddedForge() { return addedForge; }
    }

    public static final class RemoveOutcome {
        private final Result result;
        private final RegisteredForge removedForge;

        private RemoveOutcome(Result result, RegisteredForge removedForge) {
            this.result = result;
            this.removedForge = removedForge;
        }

        public static RemoveOutcome removed(RegisteredForge forge) {
            return new RemoveOutcome(Result.REMOVED, forge);
        }

        public static RemoveOutcome notFound(RegisteredForge forge) {
            return new RemoveOutcome(Result.NOT_FOUND, forge);
        }

        public static RemoveOutcome persistenceFailed(RegisteredForge forge) {
            return new RemoveOutcome(Result.PERSISTENCE_FAILED, forge);
        }

        public Result getResult() { return result; }
        public RegisteredForge getRemovedForge() { return removedForge; }
        public String getReference() { return removedForge != null ? removedForge.getId() : null; }
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
}
