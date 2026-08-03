package com.arkflame.flameforge.menu;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SimpleInventoryFactory implements InventoryFactory {
    @Override
    public Inventory create(InventoryHolder holder, int size, String legacyTitle) {
        return Bukkit.createInventory(holder, size, legacyTitle);
    }
}