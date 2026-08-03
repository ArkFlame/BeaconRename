package com.arkflame.flameforge.menu;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class InventoryMenuBuilder {
    private final InventoryFactory factory;
    private final InventoryHolder holder;
    private final int size;
    private final String legacyTitle;
    private ItemStack background;
    private final Map<Integer, ItemStack> slotOverlays = new HashMap<>();
    private boolean built = false;

    public InventoryMenuBuilder(InventoryFactory factory, InventoryHolder holder, int size, String legacyTitle) {
        this.factory = Objects.requireNonNull(factory);
        if (size <= 0 || size % 9 != 0) {
            throw new IllegalArgumentException("Size must be positive and divisible by 9");
        }
        this.holder = Objects.requireNonNull(holder);
        this.size = size;
        this.legacyTitle = Objects.requireNonNull(legacyTitle);
    }

    public InventoryMenuBuilder background(ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("Background item cannot be null");
        }
        this.background = item.clone();
        return this;
    }

    public InventoryMenuBuilder slot(int slot, ItemStack item) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("Slot out of bounds: " + slot);
        }
        if (item == null) {
            throw new IllegalArgumentException("Slot item cannot be null");
        }
        this.slotOverlays.put(slot, item.clone());
        return this;
    }

    public InventoryMenuBuilder restoreBackground(int slot) {
        this.slotOverlays.remove(slot);
        return this;
    }

    public Inventory build() {
        if (built) {
            throw new IllegalStateException("Builder can only be built once");
        }
        if (background == null) {
            throw new IllegalStateException("Background must be set before build");
        }
        built = true;
        Inventory inv = factory.create(holder, size, legacyTitle);
        ItemStack bg = background.clone();
        for (int i = 0; i < size; i++) {
            inv.setItem(i, bg);
        }
        for (Map.Entry<Integer, ItemStack> e : slotOverlays.entrySet()) {
            inv.setItem(e.getKey(), e.getValue());
        }
        return inv;
    }
}
