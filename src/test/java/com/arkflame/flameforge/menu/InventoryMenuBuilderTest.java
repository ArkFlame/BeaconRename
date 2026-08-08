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
    void testBuiltInventoryRendersCorrectSlotContents() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);
        Inventory mockInventory = mock(Inventory.class);

        java.util.Map<Integer, ItemStack> storedItems = new java.util.HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any(ItemStack.class));

        when(factory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        InventoryMenuBuilder builder = new InventoryMenuBuilder(factory, holder, 27, "Test Menu");

        ItemStack bg = new ItemStack(Material.STONE);
        builder.background(bg);

        ItemStack slot4Item = new ItemStack(Material.DIAMOND);
        builder.slot(4, slot4Item);
        builder.slot(13, new ItemStack(Material.GOLD_INGOT));
        builder.slot(22, new ItemStack(Material.IRON_INGOT));

        builder.restoreBackground(4);

        Inventory inv = builder.build();

        assertEquals(Material.STONE, inv.getItem(4).getType());
        assertEquals(Material.GOLD_INGOT, inv.getItem(13).getType());
        assertEquals(Material.IRON_INGOT, inv.getItem(22).getType());
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
    void testBuiltInventoryFillsUnsetSlotsWithBackground() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);
        Inventory mockInventory = mock(Inventory.class);

        java.util.Map<Integer, ItemStack> storedItems = new java.util.HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any(ItemStack.class));

        when(factory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        InventoryMenuBuilder builder = new InventoryMenuBuilder(factory, holder, 27, "Test Menu");

        ItemStack bg = new ItemStack(Material.COBBLESTONE);
        builder.background(bg);

        builder.slot(4, new ItemStack(Material.DIAMOND));

        Inventory inv = builder.build();

        assertEquals(Material.COBBLESTONE, inv.getItem(0).getType());
        assertEquals(Material.COBBLESTONE, inv.getItem(1).getType());
        assertEquals(Material.COBBLESTONE, inv.getItem(2).getType());
        assertEquals(Material.COBBLESTONE, inv.getItem(3).getType());
        assertEquals(Material.DIAMOND, inv.getItem(4).getType());
        assertEquals(Material.COBBLESTONE, inv.getItem(5).getType());
    }

    @Test
    void applyToExistingInventoryUpdatesContentsWithoutFactoryCreate() {
        InventoryHolder holder = mock(InventoryHolder.class);
        InventoryFactory factory = mock(InventoryFactory.class);
        Inventory mockInventory = mock(Inventory.class);

        java.util.Map<Integer, ItemStack> storedItems = new java.util.HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any());
        when(mockInventory.getSize()).thenReturn(27);

        InventoryMenuBuilder builder = new InventoryMenuBuilder(factory, holder, 27, "Test Menu");

        ItemStack bg = new ItemStack(Material.STONE);
        builder.background(bg);

        ItemStack overlay = new ItemStack(Material.DIAMOND);
        builder.slot(4, overlay);

        ItemStack explicitEmptyOrig = new ItemStack(Material.GOLD_INGOT);
        storedItems.put(13, explicitEmptyOrig);
        builder.empty(13);

        builder.applyTo(mockInventory);

        assertEquals(Material.DIAMOND, mockInventory.getItem(4).getType());
        assertEquals(Material.STONE, mockInventory.getItem(0).getType());
        assertEquals(null, storedItems.get(13));

        verify(factory, never()).create(any(), anyInt(), anyString());

        ItemStack slot4Item = mockInventory.getItem(4);
        assertNotSame(overlay, slot4Item);

        assertThrows(IllegalStateException.class, () -> builder.applyTo(mockInventory));
        assertThrows(IllegalStateException.class, () -> builder.build());
    }
}
