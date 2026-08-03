package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PendingDeliveryRepository {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final Path deliveriesFile;
    private final ConcurrentHashMap<String, PendingDelivery> byId = new ConcurrentHashMap<>();
    private final AtomicBoolean dropsLogged = new AtomicBoolean(false);
    private final Object writeLock = new Object();
    private CompletableFuture<Void> currentWrite = CompletableFuture.completedFuture(null);

    public PendingDeliveryRepository(JavaPlugin plugin, SchedulerBridge scheduler, Path dataFolder) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.deliveriesFile = dataFolder.resolve("pending-deliveries.yml");
    }

    public void load() {
        try {
            loadFromDisk();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load pending deliveries: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> loadAsync() {
        return scheduleAsync(this::loadFromDisk);
    }

    private void loadFromDisk() {
        Map<String, PendingDelivery> loaded = new java.util.HashMap<>();
        if (!Files.exists(deliveriesFile)) {
            byId.clear();
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(deliveriesFile.toFile());
        List<?> list = config.getList("deliveries", Collections.emptyList());
        for (Object item : list) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                PendingDelivery delivery = fromMap(map);
                if (delivery != null) {
                    loaded.put(delivery.getDeliveryId(), delivery);
                }
            }
        }
        byId.clear();
        byId.putAll(loaded);
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

    private PendingDelivery fromMap(Map<String, Object> map) {
        try {
            String id = (String) map.get("deliveryId");
            String uuidStr = (String) map.get("targetPlayer");
            long createdAt = map.get("createdAt") instanceof Number ? ((Number) map.get("createdAt")).longValue() : System.currentTimeMillis();
            @SuppressWarnings("unchecked")
            Map<String, Object> items = map.get("items") instanceof Map ? (Map<String, Object>) map.get("items") : Collections.emptyMap();
            @SuppressWarnings("unchecked")
            List<String> commands = map.get("commands") instanceof List ? (List<String>) map.get("commands") : Collections.emptyList();
            if (id == null || uuidStr == null) {
                return null;
            }
            return new PendingDelivery(id, UUID.fromString(uuidStr), createdAt, items, commands);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse pending delivery: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> toMap(PendingDelivery delivery) {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("deliveryId", delivery.getDeliveryId());
        map.put("targetPlayer", delivery.getTargetPlayer().toString());
        map.put("createdAt", delivery.getCreatedAt());
        map.put("items", new java.util.HashMap<>(delivery.getItemSnapshot()));
        map.put("commands", new ArrayList<>(delivery.getDeferredCommands()));
        return map;
    }

    public boolean addDelivery(PendingDelivery delivery) {
        return byId.putIfAbsent(delivery.getDeliveryId(), delivery) == null;
    }

    public PendingDelivery getDelivery(String deliveryId) {
        return byId.get(deliveryId);
    }

    public boolean contains(String deliveryId) {
        return byId.containsKey(deliveryId);
    }

    public Set<String> removeAfterConfirmed(String deliveryId) {
        PendingDelivery removed = byId.remove(deliveryId);
        if (removed == null) {
            return Collections.emptySet();
        }
        saveAsync();
        Set<String> commands = new HashSet<>(removed.getDeferredCommands());
        return commands;
    }

    public CompletableFuture<Void> saveAsync() {
        synchronized (writeLock) {
            CompletableFuture<Void> previous = currentWrite;
            CompletableFuture<Void> next = new CompletableFuture<>();
            currentWrite = next;
            previous.whenComplete((ignored, failure) -> scheduleSave(next));
            return next;
        }
    }

    private void scheduleSave(CompletableFuture<Void> result) {
        try {
            scheduler.runAsync(plugin, () -> {
                try {
                    save();
                    result.complete(null);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
    }

    private void save() throws IOException {
            Files.createDirectories(deliveriesFile.getParent());
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            List<Map<String, Object>> list = new ArrayList<>();
            for (PendingDelivery delivery : byId.values()) {
                list.add(toMap(delivery));
            }
            config.set("deliveries", list);
            config.save(deliveriesFile.toFile());
    }

    public CompletableFuture<Void> currentWrite() {
        synchronized (writeLock) {
            return currentWrite;
        }
    }

    public List<PendingDelivery> getAllSnapshot() {
        return new ArrayList<>(byId.values());
    }
}
