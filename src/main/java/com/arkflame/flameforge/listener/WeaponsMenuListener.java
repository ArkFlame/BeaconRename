package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.menu.WeaponsMenuHolder;
import com.arkflame.flameforge.menu.WeaponsMenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

public final class WeaponsMenuListener implements Listener {

    private final WeaponsMenuService menuService;

    public WeaponsMenuListener(WeaponsMenuService menuService) {
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null || !(topInventory.getHolder() instanceof WeaponsMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || clickedInventory != topInventory) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= WeaponsMenuService.SIZE) {
            return;
        }
        WeaponsMenuHolder holder = (WeaponsMenuHolder) topInventory.getHolder();
        menuService.handleClick((Player) event.getWhoClicked(), holder.getPage(), rawSlot);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory != null && topInventory.getHolder() instanceof WeaponsMenuHolder) {
            event.setCancelled(true);
        }
    }
}
