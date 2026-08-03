package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.menu.ForgeInventoryHolder;
import com.arkflame.flameforge.menu.ForgeMenuContext;
import com.arkflame.flameforge.menu.ForgeMenuService;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

public final class ForgeInventoryListener implements Listener {

    private static final int SLOT_INPUT = 13;
    private static final int SLOT_CONFIRM = 22;
    private static final int SLOT_CLOSE = 26;

    private final JavaPlugin plugin;
    private final ForgeMenuService menuService;
    private final SchedulerBridge scheduler;

    public ForgeInventoryListener(JavaPlugin plugin, ForgeMenuService menuService,
                                  SchedulerBridge scheduler) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder genericHolder = inventory.getHolder();
        if (!(genericHolder instanceof ForgeInventoryHolder)) {
            return;
        }
        ForgeInventoryHolder holder = (ForgeInventoryHolder) genericHolder;

        Player player = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : null;
        if (player == null) {
            event.setCancelled(true);
            return;
        }

        if (!menuService.isCurrentMenu(player, holder)) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();

        if (event.getInventory().getType() == InventoryType.PLAYER) {
            if (event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick().isShiftClick()) {
                handleBottomShiftClick(player, holder, event);
                return;
            }
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                handleBottomMoveToTop(player, holder, event);
                return;
            }
            return;
        }

        if (rawSlot < 0 || rawSlot >= inventory.getSize()) {
            event.setCancelled(true);
            return;
        }

        ClickType click = event.getClick();
        if (click == ClickType.CREATIVE || click == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (rawSlot == SLOT_INPUT) {
            handleInputClick(player, holder);
            return;
        }

        if (rawSlot == SLOT_CONFIRM) {
            handleConfirmClick(player, holder);
            return;
        }

        if (rawSlot == SLOT_CLOSE) {
            handleCloseClick(player, holder);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder genericHolder = inventory.getHolder();
        if (!(genericHolder instanceof ForgeInventoryHolder)) {
            return;
        }
        ForgeInventoryHolder holder = (ForgeInventoryHolder) genericHolder;

        Player player = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : null;
        if (player == null) {
            event.setCancelled(true);
            return;
        }

        if (!menuService.isCurrentMenu(player, holder)) {
            event.setCancelled(true);
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < inventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event == null) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder genericHolder = inventory.getHolder();
        if (!(genericHolder instanceof ForgeInventoryHolder)) {
            return;
        }
        ForgeInventoryHolder holder = (ForgeInventoryHolder) genericHolder;

        Player player = event.getPlayer() instanceof Player ? (Player) event.getPlayer() : null;
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Optional<ForgeMenuContext> ctxOpt = menuService.closeIfCurrent(playerId, holder);
        if (!ctxOpt.isPresent()) {
            return;
        }

        ForgeMenuContext ctx = ctxOpt.get();
        if (ctx.isForging()) {
            return;
        }

        if (ctx.isOpen()) {
            scheduler.runEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Optional<ItemStack> returned = ctx.extractInput();
                if (returned.isPresent()) {
                    giveItemToPlayer(player, returned.get());
                }
            }, () -> {});
        }
    }

    private void handleInputClick(Player player, ForgeInventoryHolder holder) {
        UUID playerId = player.getUniqueId();
        ForgeMenuContext ctx = menuService.getContext(playerId);
        if (ctx == null || !ctx.isOpen()) {
            return;
        }

        scheduler.runEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Optional<ItemStack> returned = ctx.extractInput();
            if (returned.isPresent()) {
                giveItemToPlayer(player, returned.get());
            }
            menuService.rerender(player);
        }, () -> {});
    }

    private void handleConfirmClick(Player player, ForgeInventoryHolder holder) {
        UUID playerId = player.getUniqueId();
        ForgeMenuContext ctx = menuService.getContext(playerId);
        if (ctx == null || !ctx.isOpen()) {
            return;
        }

        scheduler.runEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (ctx.beginForge()) {
                PlayerForgeState session = ctx.getSession();
                if (session != null) {
                    menuService.refresh(player, session);
                }
            }
        }, () -> {});
    }

    private void handleCloseClick(Player player, ForgeInventoryHolder holder) {
        UUID playerId = player.getUniqueId();
        Optional<ForgeMenuContext> ctxOpt = menuService.closeIfCurrent(playerId, holder);
        if (!ctxOpt.isPresent()) {
            player.closeInventory();
            return;
        }

        ForgeMenuContext ctx = ctxOpt.get();
        if (ctx.isForging()) {
            player.closeInventory();
            return;
        }

        scheduler.runEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Optional<ItemStack> returned = ctx.extractInput();
            if (returned.isPresent()) {
                giveItemToPlayer(player, returned.get());
            }
            player.closeInventory();
        }, () -> {});
    }

    private void handleBottomShiftClick(Player player, ForgeInventoryHolder holder, InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) {
            return;
        }

        UUID playerId = player.getUniqueId();
        ForgeMenuContext ctx = menuService.getContext(playerId);
        if (ctx == null || !ctx.isOpen()) {
            event.setCancelled(true);
            return;
        }

        ItemStack toInsert = currentItem.clone();
        toInsert.setAmount(1);

        if (!ctx.tryInsert(toInsert)) {
            event.setCancelled(true);
            return;
        }

        ItemStack remainder = currentItem.clone();
        remainder.setAmount(currentItem.getAmount() - 1);
        if (remainder.getAmount() <= 0) {
            event.setCurrentItem(null);
        } else {
            event.setCurrentItem(remainder);
        }

        scheduler.runEntity(player, () -> {
            if (player.isOnline()) {
                menuService.rerender(player);
            }
        }, () -> {});
    }

    private void handleBottomMoveToTop(Player player, ForgeInventoryHolder holder, InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) {
            return;
        }

        UUID playerId = player.getUniqueId();
        ForgeMenuContext ctx = menuService.getContext(playerId);
        if (ctx == null || !ctx.isOpen()) {
            event.setCancelled(true);
            return;
        }

        ItemStack toInsert = currentItem.clone();
        toInsert.setAmount(1);

        if (!ctx.tryInsert(toInsert)) {
            event.setCancelled(true);
            return;
        }

        ItemStack remainder = currentItem.clone();
        remainder.setAmount(currentItem.getAmount() - 1);
        if (remainder.getAmount() <= 0) {
            event.setCurrentItem(null);
        } else {
            event.setCurrentItem(remainder);
        }

        scheduler.runEntity(player, () -> {
            if (player.isOnline()) {
                menuService.rerender(player);
            }
        }, () -> {});
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack overflowItem : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
        }
    }

}
