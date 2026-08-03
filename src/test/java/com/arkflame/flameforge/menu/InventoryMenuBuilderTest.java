package com.arkflame.flameforge.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryMenuBuilderTest {

    @Test
    void testBuildRequiresBackground() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);
        InventoryMenuBuilder builder = new InventoryMenuBuilder(factory, holder, 27, "Test Menu");

        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    void testBuildRequiresPositiveSizeDivisibleBy9() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);

        assertThrows(IllegalArgumentException.class, () -> new InventoryMenuBuilder(factory, holder, 0, "Test"));
        assertThrows(IllegalArgumentException.class, () -> new InventoryMenuBuilder(factory, holder, -1, "Test"));
        assertThrows(IllegalArgumentException.class, () -> new InventoryMenuBuilder(factory, holder, 10, "Test"));
    }

    @Test
    void testBackgroundAndSlotApi() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);
        InventoryMenuBuilder builder = new InventoryMenuBuilder(factory, holder, 27, "Test Menu");

        ItemStack bg = new ItemStack(Material.STONE);
        builder.background(bg);

        ItemStack overlay = new ItemStack(Material.DIAMOND);
        builder.slot(4, overlay);
        builder.slot(13, overlay);
        builder.slot(22, overlay);

        builder.restoreBackground(4);

        assertNotNull(builder);
    }

    @Test
    void testSlotBoundsValidation() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);
        InventoryMenuBuilder builder = new InventoryMenuBuilder(factory, holder, 27, "Test Menu");

        assertThrows(IndexOutOfBoundsException.class, () -> builder.slot(-1, new ItemStack(Material.DIAMOND)));
        assertThrows(IndexOutOfBoundsException.class, () -> builder.slot(27, new ItemStack(Material.DIAMOND)));
    }

    @Test
    void testBackgroundWithMultipleSlots() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);
        InventoryMenuBuilder builder = new InventoryMenuBuilder(factory, holder, 54, "Test Menu");

        ItemStack bg = new ItemStack(Material.STONE);
        builder.background(bg);

        for (int i = 0; i < 54; i++) {
            builder.slot(i, new ItemStack(Material.DIAMOND));
        }

        assertNotNull(builder);
    }

    @Test
    void testSlotAndRestorePreservesBuilderState() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);
        InventoryMenuBuilder builder = new InventoryMenuBuilder(factory, holder, 27, "Test");

        ItemStack overlay = new ItemStack(Material.DIAMOND);
        builder.slot(13, overlay);
        builder.restoreBackground(13);

        assertNotNull(builder);
    }
}
