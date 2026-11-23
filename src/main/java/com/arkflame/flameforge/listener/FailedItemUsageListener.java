package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.item.ItemIdentityService;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Blocks cursed/fractured items from being used as an ingredient in crafting, anvil
 * combining or smelting. The vanilla result is cleared as well as the event cancelled
 * so a stale result cannot be shift-clicked out of the inventory.
 */
public final class FailedItemUsageListener implements Listener {
    private final ItemIdentityService identityService;

    public FailedItemUsageListener(ItemIdentityService identityService) {
        this.identityService = identityService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (inventory == null || !FailedItemGuard.anyFailed(identityService, inventory.getMatrix())) {
            return;
        }
        inventory.setResult(null);
        cancel(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }
        if (FailedItemGuard.isFailed(identityService, inventory.getItem(0))
                || FailedItemGuard.isFailed(identityService, inventory.getItem(1))) {
            event.setResult(null);
            cancel(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResultClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory)
                && !(event.getInventory() instanceof AnvilInventory)) {
            return;
        }
        boolean resultSlot = event.getInventory() instanceof AnvilInventory
                ? event.getRawSlot() == 2 : event.getRawSlot() == 0;
        if (!resultSlot) {
            return;
        }
        ItemStack[] ingredients = event.getInventory() instanceof AnvilInventory
                ? new ItemStack[]{((AnvilInventory) event.getInventory()).getItem(0),
                    ((AnvilInventory) event.getInventory()).getItem(1)}
                : ((CraftingInventory) event.getInventory()).getMatrix();
        if (!FailedItemGuard.anyFailed(identityService, ingredients)) {
            return;
        }
        if (event.getInventory() instanceof CraftingInventory) {
            ((CraftingInventory) event.getInventory()).setResult(null);
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (FailedItemGuard.isFailed(identityService, event.getSource())) {
            event.setResult(null);
            event.setCancelled(true);
        }
    }

    private void cancel(org.bukkit.event.Event event) {
        if (event instanceof Cancellable) {
            ((Cancellable) event).setCancelled(true);
        }
    }
}
