package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerStateRepository {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final Path playerDataFolder;
    private final ConcurrentHashMap<UUID, PlayerState> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public PlayerStateRepository(JavaPlugin plugin, SchedulerBridge scheduler, Path dataFolder) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.playerDataFolder = dataFolder.resolve("player-data");
    }

    public void loadAllBlocking() {
        CountDownLatch latch = new CountDownLatch(1);
        scheduler.runAsync(plugin, () -> {
            loadAll();
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Player state load interrupted");
        }
        initialized.set(true);
    }

    private void loadAll() {
        Path folder = playerDataFolder;
        if (!Files.exists(folder)) {
            return;
        }
        try {
            Files.list(folder)
                .filter(p -> p.toString().endsWith(".yml"))
                .forEach(this::loadFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load player states: " + e.getMessage());
        }
    }

    private void loadFile(Path file) {
        try {
            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
            String name = file.getFileName().toString();
            String uuidStr = name.substring(0, name.length() - 4);
            UUID uuid = UUID.fromString(uuidStr);
            int tier = config.getInt("tier", 0);
            long pityCooldown = config.getLong("pityCooldown", 0L);
            PlayerState state = new PlayerState(uuid, tier, pityCooldown);
            cache.put(uuid, state);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load player state from " + file + ": " + e.getMessage());
        }
    }

    public PlayerState getOrLoad(UUID uuid) {
        PlayerState existing = cache.get(uuid);
        if (existing != null) {
            return existing;
        }
        PlayerState newState = new PlayerState(uuid, 0, 0L);
        PlayerState raced = cache.putIfAbsent(uuid, newState);
        return raced != null ? raced : newState;
    }

    public void saveAsync(UUID uuid, PlayerState state) {
        scheduler.runAsync(plugin, () -> save(uuid, state));
    }

    private void save(UUID uuid, PlayerState state) {
        try {
            Files.createDirectories(playerDataFolder);
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            config.set("tier", state.tier);
            config.set("pityCooldown", state.pityCooldown);
            config.save(playerDataFolder.resolve(uuid.toString() + ".yml").toFile());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player state for " + uuid + ": " + e.getMessage());
        }
    }

    public boolean atomicReplace(UUID uuid, PlayerState expected, PlayerState replacement) {
        if (cache.replace(uuid, expected, replacement)) {
            saveAsync(uuid, replacement);
            return true;
        }
        return false;
    }

    public PlayerState getSnapshot(UUID uuid) {
        PlayerState state = cache.get(uuid);
        return state != null ? state.copy() : new PlayerState(uuid, 0, 0L);
    }

    public void updateAndSave(UUID uuid, java.util.function.UnaryOperator<PlayerState> updater) {
        while (true) {
            PlayerState current = cache.get(uuid);
            if (current == null) {
                current = new PlayerState(uuid, 0, 0L);
            }
            PlayerState updated = updater.apply(current);
            if (cache.replace(uuid, current, updated)) {
                saveAsync(uuid, updated);
                return;
            }
        }
    }

    public static final class PlayerState {
        public final UUID uuid;
        public final int tier;
        public final long pityCooldown;

        public PlayerState(UUID uuid, int tier, long pityCooldown) {
            this.uuid = uuid;
            this.tier = tier;
            this.pityCooldown = pityCooldown;
        }

        public PlayerState withTier(int tier) {
            return new PlayerState(uuid, tier, this.pityCooldown);
        }

        public PlayerState withPityCooldown(long pityCooldown) {
            return new PlayerState(uuid, this.tier, pityCooldown);
        }

        public PlayerState copy() {
            return new PlayerState(uuid, tier, pityCooldown);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlayerState)) return false;
            PlayerState that = (PlayerState) o;
            return Objects.equals(uuid, that.uuid) && tier == that.tier && pityCooldown == that.pityCooldown;
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, tier, pityCooldown);
        }
    }
}
