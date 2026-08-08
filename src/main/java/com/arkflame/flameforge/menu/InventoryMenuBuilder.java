package com.arkflame.flameforge.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class InventoryMenuBuilder {
    private final InventoryFactory factory;
    private final InventoryHolder holder;
    private final int size;
    private final String legacyTitle;
    private ItemStack background;
    private final Map<Integer, ItemStack> slotOverlays = new HashMap<>();
    private final Set<Integer> explicitEmptySlots = new HashSet<>();
    private boolean rendered = false;

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
        this.explicitEmptySlots.remove(slot);
        this.slotOverlays.put(slot, item.clone());
        return this;
    }

    public InventoryMenuBuilder restoreBackground(int slot) {
        this.slotOverlays.remove(slot);
        this.explicitEmptySlots.remove(slot);
        return this;
    }

    public InventoryMenuBuilder empty(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("Slot out of bounds: " + slot);
        }
        this.slotOverlays.remove(slot);
        this.explicitEmptySlots.add(slot);
        return this;
    }

    public Inventory build() {
        claimRender();
        if (background == null) {
            throw new IllegalStateException("Background must be set before build");
        }
        Inventory inv = factory.create(holder, size, legacyTitle);
        renderInto(inv);
        return inv;
    }

    public void applyTo(Inventory inventory) {
        claimRender();
        if (background == null) {
            throw new IllegalStateException("Background must be set before applyTo");
        }
        Objects.requireNonNull(inventory, "Inventory cannot be null");
        if (inventory.getSize() != size) {
            throw new IllegalArgumentException("Inventory size mismatch: expected " + size + ", got " + inventory.getSize());
        }
        renderInto(inventory);
    }

    private void claimRender() {
        if (rendered) {
            throw new IllegalStateException("Builder can only be rendered once");
        }
        rendered = true;
    }

    private void renderInto(Inventory inventory) {
        for (int i = 0; i < size; i++) {
            if (explicitEmptySlots.contains(i)) {
                inventory.setItem(i, null);
            } else if (slotOverlays.containsKey(i)) {
                inventory.setItem(i, slotOverlays.get(i).clone());
            } else {
                inventory.setItem(i, background.clone());
            }
        }
    }
}
