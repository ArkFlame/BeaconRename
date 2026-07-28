package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StationRepository {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final Path dataFolder;
    private final Path stationsFile;
    private final Map<String, StationData> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public StationRepository(JavaPlugin plugin, SchedulerBridge scheduler, Path dataFolder) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.dataFolder = dataFolder;
        this.stationsFile = dataFolder.resolve("stations.yml");
    }

    public void load() {
        File file = stationsFile.toFile();
        if (!file.exists()) {
            initialized.set(true);
            return;
        }
        try {
            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            for (String key : config.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection(key);
                if (section != null) {
                    String world = section.getString("world");
                    double x = section.getDouble("x");
                    double y = section.getDouble("y");
                    double z = section.getDouble("z");
                    String profile = section.getString("profile", "default");
                    String id = section.getString("id", key);
                    cache.put(key, new StationData(id, world, x, y, z, profile));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load stations: " + e.getMessage());
        }
        initialized.set(true);
    }

    public void saveAsync(Runnable onComplete) {
        scheduler.runAsync(plugin, () -> {
            save();
            if (onComplete != null) {
                scheduler.runGlobal(plugin, onComplete);
            }
        });
    }

    private synchronized void save() {
        try {
            Files.createDirectories(dataFolder);
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            for (Map.Entry<String, StationData> entry : cache.entrySet()) {
                String key = entry.getKey();
                StationData data = entry.getValue();
                org.bukkit.configuration.ConfigurationSection section = config.createSection(key);
                section.set("id", data.id);
                section.set("world", data.world);
                section.set("x", data.x);
                section.set("y", data.y);
                section.set("z", data.z);
                section.set("profile", data.profile);
            }
            config.save(stationsFile.toFile());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save stations: " + e.getMessage());
        }
    }

    public boolean addStation(String id, String world, double x, double y, double z, String profile) {
        if (!initialized.get()) {
            throw new IllegalStateException("Repository not initialized");
        }
        for (StationData existing : cache.values()) {
            if (existing.world.equals(world) && existing.x == x && existing.y == y && existing.z == z) {
                return false;
            }
            if (existing.id.equals(id)) {
                return false;
            }
        }
        String key = world + "_" + x + "_" + y + "_" + z;
        cache.put(key, new StationData(id, world, x, y, z, profile));
        return true;
    }

    public Map<String, StationData> getAllSnapshot() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<>(cache));
    }

    public StationData getByKey(String key) {
        return cache.get(key);
    }

    public void remove(String key) {
        cache.remove(key);
    }

    public static final class StationData {
        public final String id;
        public final String world;
        public final double x, y, z;
        public final String profile;

        public StationData(String id, String world, double x, double y, double z, String profile) {
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
