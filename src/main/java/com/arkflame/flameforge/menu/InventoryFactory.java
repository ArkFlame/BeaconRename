package com.arkflame.flameforge.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public interface InventoryFactory {
    Inventory create(InventoryHolder holder, int size, String legacyTitle);
}
