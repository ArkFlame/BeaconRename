package com.arkflame.flameforge.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class ForgeInventoryHolder implements InventoryHolder {
    private final UUID menuId;
    private final UUID playerId;
    private final String stationId;
    private Inventory inventory;

    public ForgeInventoryHolder(UUID menuId, UUID playerId, String stationId) {
        this.menuId = menuId;
        this.playerId = playerId;
        this.stationId = stationId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getMenuId() {
        return menuId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getStationId() {
        return stationId;
    }
}
