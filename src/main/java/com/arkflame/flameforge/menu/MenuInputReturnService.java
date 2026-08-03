package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.forge.DeliveryService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class MenuInputReturnService {
    private final DeliveryService deliveryService;

    public MenuInputReturnService(DeliveryService deliveryService) {
        this.deliveryService = Objects.requireNonNull(deliveryService);
    }

    public void returnToPlayer(ItemStack item, Player player) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return;
        }
        Objects.requireNonNull(player);

        String deliveryId = deliveryService.generateDeliveryId(player, "menu_return");
        deliveryService.deliverItem(item, player, player.getLocation(), deliveryId);
    }

    public void returnToPlayerOffline(ItemStack item, UUID playerId) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return;
        }
        Objects.requireNonNull(playerId);

        String deliveryId = "menu_return_" + playerId.toString() + "_" + System.nanoTime();
        deliveryService.queuePendingDelivery(deliveryId, playerId, item, null);
    }
}
