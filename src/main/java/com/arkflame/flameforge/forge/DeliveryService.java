package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDelivery;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.text.TextBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class DeliveryService {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final PendingDeliveryRepository deliveryRepository;
    private final TextBridge textBridge;
    private final AuditLogService auditLog;
    private final Map<String, Object> announcementConfig;
    private final ConcurrentHashMap<String, Boolean> processedDeliveryIds = new ConcurrentHashMap<>();
    private final Set<String> loggedMissingMaterials = new HashSet<>();

    public DeliveryService(JavaPlugin plugin, SchedulerBridge scheduler,
                          PendingDeliveryRepository deliveryRepository,
                          TextBridge textBridge, AuditLogService auditLog,
                          Map<String, Object> announcementConfig) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository);
        this.textBridge = Objects.requireNonNull(textBridge);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.announcementConfig = announcementConfig != null ? announcementConfig : new HashMap<>();
    }

    public String generateDeliveryId(Player player, String outcomeId) {
        String timestamp = String.valueOf(System.nanoTime());
        String playerId = player != null ? player.getUniqueId().toString() : "console";
        return outcomeId + "_" + playerId + "_" + timestamp;
    }

    public boolean deliverItem(ItemStack item, Player player, Location location, String deliveryId) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (deliveryId != null && !isIdempotentDelivery(deliveryId)) {
            return false;
        }

        if (player != null && player.isOnline()) {
            return deliverToOnlinePlayer(item, player, deliveryId);
        } else if (location != null) {
            return deliverToLocation(item, location, deliveryId);
        } else {
            return false;
        }
    }

    private boolean deliverToOnlinePlayer(ItemStack item, Player player, String deliveryId) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (overflow.isEmpty()) {
            auditLog.logAsync("ITEM_DELIVERED", player.getName(), deliveryId,
                "Item delivered to inventory: " + item.getType());
            return true;
        } else {
            Location dropLocation = player.getLocation();
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(dropLocation, overflowItem);
            }
            auditLog.logAsync("ITEM_DELIVERED_PARTIAL", player.getName(), deliveryId,
                "Partial delivery, overflow dropped: " + overflow.size() + " items");
            return true;
        }
    }

    private boolean deliverToLocation(ItemStack item, Location location, String deliveryId) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        world.dropItemNaturally(location, item);
        auditLog.logAsync("ITEM_DROPPED", "location", deliveryId,
            "Item dropped at location: " + item.getType());
        return true;
    }

    public boolean queuePendingDelivery(String deliveryId, UUID targetPlayer,
                                         ItemStack item, List<String> commands) {
        if (deliveryId == null) {
            return false;
        }

        if (processedDeliveryIds.containsKey(deliveryId)) {
            return false;
        }

        Map<String, Object> itemSnapshot = new HashMap<>();
        if (item != null && item.getType() != Material.AIR) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
                oos.writeObject(item);
                oos.flush();
                itemSnapshot.put("serialized", Base64.getEncoder().encodeToString(baos.toByteArray()));
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to serialize item for delivery: " + e.getMessage());
            }
            itemSnapshot.put("material", item.getType().name());
            itemSnapshot.put("amount", item.getAmount());
        }

        List<String> deferredCommands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();

        PendingDelivery delivery = new PendingDelivery(
            deliveryId,
            targetPlayer != null ? targetPlayer : new UUID(0, 0),
            System.currentTimeMillis(),
            itemSnapshot,
            deferredCommands
        );

        boolean added = deliveryRepository.addDelivery(delivery);
        if (added) {
            auditLog.logAsync("DELIVERY_QUEUED", targetPlayer != null ? targetPlayer.toString() : "console",
                deliveryId, "Pending delivery queued");
        }
        return added;
    }

    public void processPlayerJoin(Player player) {
        UUID playerUuid = player.getUniqueId();
        List<PendingDelivery> playerDeliveries = deliveryRepository.getAllSnapshot().stream()
            .filter(d -> d.getTargetPlayer().equals(playerUuid))
            .collect(Collectors.toList());

        for (PendingDelivery delivery : playerDeliveries) {
            processDeliveryOnJoin(player, delivery);
        }
    }

    private void processDeliveryOnJoin(Player player, PendingDelivery delivery) {
        String deliveryId = delivery.getDeliveryId();

        if (!processedDeliveryIds.containsKey(deliveryId)) {
            processedDeliveryIds.put(deliveryId, Boolean.TRUE);
        }

        ItemStack item = reconstructItem(delivery.getItemSnapshot());
        if (item != null) {
            deliverToOnlinePlayer(item, player, deliveryId);
        }

        List<String> commands = new ArrayList<>(delivery.getDeferredCommands());
        if (!commands.isEmpty()) {
            for (String command : commands) {
                String resolved = resolveCommandPlaceholders(command, player.getName(), player.getUniqueId());
                try {
                    if (resolved.startsWith("/")) {
                        resolved = resolved.substring(1);
                    }
                    boolean success = player.performCommand(resolved);
                    if (!success) {
                        auditLog.logAsync("DEFERRED_COMMAND_FAIL", player.getName(), deliveryId,
                            "Deferred command failed: " + command);
                        return;
                    }
                } catch (Exception e) {
                    auditLog.logAsync("DEFERRED_COMMAND_ERROR", player.getName(), deliveryId,
                        "Deferred command error: " + command + " - " + e.getMessage());
                    return;
                }
            }
        }

        deliveryRepository.removeAfterConfirmed(deliveryId);
        auditLog.logAsync("DELIVERY_COMPLETE", player.getName(), deliveryId,
            "Pending delivery completed on join");
    }

    public void processGlobalContext() {
        List<PendingDelivery> consoleDeliveries = deliveryRepository.getAllSnapshot().stream()
            .filter(d -> d.getTargetPlayer().getMostSignificantBits() == 0
                && d.getTargetPlayer().getLeastSignificantBits() == 0)
            .collect(Collectors.toList());

        for (PendingDelivery delivery : consoleDeliveries) {
            processConsoleDelivery(delivery);
        }
    }

    private void processConsoleDelivery(PendingDelivery delivery) {
        String deliveryId = delivery.getDeliveryId();

        if (!processedDeliveryIds.containsKey(deliveryId)) {
            processedDeliveryIds.put(deliveryId, Boolean.TRUE);
        }

        List<String> commands = new ArrayList<>(delivery.getDeferredCommands());
        if (!commands.isEmpty()) {
            for (String command : commands) {
                try {
                    if (command.startsWith("/")) {
                        command = command.substring(1);
                    }
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    if (!success) {
                        auditLog.logAsync("DEFERRED_COMMAND_FAIL", "console", deliveryId,
                            "Deferred command failed: " + command);
                        return;
                    }
                } catch (Exception e) {
                    auditLog.logAsync("DEFERRED_COMMAND_ERROR", "console", deliveryId,
                        "Deferred command error: " + command + " - " + e.getMessage());
                    return;
                }
            }
        }

        deliveryRepository.removeAfterConfirmed(deliveryId);
        auditLog.logAsync("DELIVERY_COMPLETE", "console", deliveryId,
            "Pending delivery completed in global context");
    }

    private boolean isIdempotentDelivery(String deliveryId) {
        return processedDeliveryIds.putIfAbsent(deliveryId, Boolean.TRUE) == null;
    }

    private ItemStack reconstructItem(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }

        String serialized = (String) snapshot.get("serialized");
        if (serialized != null) {
            try {
                byte[] data = Base64.getDecoder().decode(serialized);
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais);
                ItemStack item = (ItemStack) ois.readObject();
                return item;
            } catch (IOException | ClassNotFoundException e) {
                plugin.getLogger().warning("Failed to deserialize item: " + e.getMessage());
                return null;
            }
        }

        String materialStr = (String) snapshot.get("material");
        if (materialStr == null) {
            return null;
        }

        int amount = snapshot.get("amount") instanceof Number ?
            ((Number) snapshot.get("amount")).intValue() : 1;

        java.util.Optional<MaterialResolver.ResolvedMaterial> resolved =
                MaterialResolver.getInstance().get(materialStr);
        if (!resolved.isPresent()) {
            if (loggedMissingMaterials.add(materialStr)) {
                plugin.getLogger().warning("Invalid persisted material: " + materialStr);
            }
            return null;
        }
        ItemStack item = resolved.get().toItemStack(amount);

        String displayName = (String) snapshot.get("displayName");
        if (displayName != null && item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String resolveCommandPlaceholders(String command, String playerName, UUID playerUuid) {
        String resolved = command;
        resolved = resolved.replace("%player_name%", playerName);
        resolved = resolved.replace("%player%", playerName);
        resolved = resolved.replace("%player_uuid%", playerUuid != null ? playerUuid.toString() : "");
        return resolved;
    }

    public void broadcastOutcome(String playerName, String outcomeId, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        String scope = getAnnouncementScope();
        Component component = textBridge.render(message);

        if ("global".equals(scope)) {
            textBridge.sendAll(component);
            auditLog.logAsync("ANNOUNCEMENT_GLOBAL", playerName, outcomeId, message);
        } else if ("radius".equals(scope)) {
            int radius = getAnnouncementRadius();
            broadcastToRadius(playerName, outcomeId, component, radius);
        }
    }

    private String getAnnouncementScope() {
        Object scope = announcementConfig.get("scope");
        return scope instanceof String ? (String) scope : "global";
    }

    private int getAnnouncementRadius() {
        Object radius = announcementConfig.get("radius");
        if (radius instanceof Number) {
            return ((Number) radius).intValue();
        }
        return 100;
    }

    private void broadcastToRadius(String playerName, String outcomeId, Component message, int radius) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            textBridge.sendAll(message);
            return;
        }

        Location center = player.getLocation();
        World world = center.getWorld();
        int radiusSquared = radius * radius;

        for (Player onlinePlayer : world.getPlayers()) {
            if (onlinePlayer.getLocation().distanceSquared(center) <= radiusSquared) {
                textBridge.send(onlinePlayer, message);
            }
        }

        auditLog.logAsync("ANNOUNCEMENT_RADIUS", playerName, outcomeId,
            "Broadcast to radius " + radius + ": " + message);
    }

    public boolean isDeliveryPending(String deliveryId) {
        return deliveryRepository.contains(deliveryId);
    }

    public boolean hasBeenProcessed(String deliveryId) {
        return processedDeliveryIds.containsKey(deliveryId);
    }
}
