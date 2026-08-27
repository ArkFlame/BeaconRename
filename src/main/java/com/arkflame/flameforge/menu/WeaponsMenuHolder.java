package com.arkflame.flameforge.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class WeaponsMenuHolder implements InventoryHolder {
    private final UUID viewerId;
    private final int page;
    private Inventory inventory;

    public WeaponsMenuHolder(UUID viewerId, int page) {
        this.viewerId = viewerId;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Inventory can only be set once");
        }
        this.inventory = inventory;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public int getPage() {
        return page;
    }
}
