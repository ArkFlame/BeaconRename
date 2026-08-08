package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.menu.ForgeInventoryHolder;
import com.arkflame.flameforge.menu.ForgeMenuForgeService;
import com.arkflame.flameforge.menu.ForgeMenuInputService;
import com.arkflame.flameforge.menu.ForgeMenuViewResolver;
import com.arkflame.flameforge.menu.MenuLayout;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class ForgeInventoryListener implements Listener {

    private final ForgeMenuViewResolver viewResolver;
    private final ForgeMenuInputService inputService;
    private final ForgeMenuForgeService forgeService;

    public ForgeInventoryListener(ForgeMenuViewResolver viewResolver, ForgeMenuInputService inputService,
                                  ForgeMenuForgeService forgeService) {
        this.viewResolver = viewResolver;
        this.inputService = inputService;
        this.forgeService = forgeService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event == null || event.isCancelled()) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        InventoryView view = event.getView();
        Player player = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : null;
        if (player == null) {
            return;
        }

        ForgeMenuViewResolver.ResolvedView resolved = viewResolver.resolve(player, view);

        if (resolved.getStatus() == ForgeMenuViewResolver.Status.NOT_FORGE) {
            return;
        }

        if (resolved.getStatus() == ForgeMenuViewResolver.Status.STALE) {
            event.setCancelled(true);
            inputService.requestCloseStaleView(player, resolved.getHolder());
            return;
        }

        ForgeInventoryHolder holder = resolved.getHolder();
        boolean bottomClick = (clickedInventory == resolved.getBottomInventory());

        if (bottomClick) {
            handleBottomClick(event, player, holder);
            return;
        }

        int rawSlot = event.getRawSlot();
        event.setCancelled(true);

        if (rawSlot == MenuLayout.SLOT_INPUT) {
            inputService.requestReturnInput(player, holder);
            return;
        }

        if (rawSlot == MenuLayout.SLOT_CONFIRM) {
            forgeService.requestConfirm(player, holder);
            return;
        }
    }

    private void handleBottomClick(InventoryClickEvent event, Player player, ForgeInventoryHolder holder) {
        ClickType click = event.getClick();
        Inventory clickedInventory = event.getClickedInventory();

        if (click == null) {
            return;
        }

        boolean isShiftClick = click.isShiftClick();
        boolean isMoveToOther = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY;
        boolean isDoubleClick = click == ClickType.DOUBLE_CLICK;
        boolean isCollectToCursor = event.getAction() == InventoryAction.COLLECT_TO_CURSOR;
        boolean isNumberKey = click == ClickType.NUMBER_KEY;
        boolean isLeft = click == ClickType.LEFT;
        boolean isRight = click == ClickType.RIGHT;

        boolean isCrossInventory = isShiftClick || isMoveToOther || isDoubleClick || isCollectToCursor;

        if (isCrossInventory) {
            event.setCancelled(true);
            if (isShiftClick || isMoveToOther) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && currentItem.getType() != org.bukkit.Material.AIR) {
                    inputService.requestInsertOne(player, holder, clickedInventory, event.getSlot(), currentItem);
                }
            }
            return;
        }

        if (isNumberKey) {
            return;
        }

        if ((isLeft || isRight)) {
            ItemStack currentItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            if (currentItem != null && currentItem.getType() != org.bukkit.Material.AIR) {
                if (cursorItem == null || cursorItem.getType() == org.bukkit.Material.AIR) {
                    event.setCancelled(true);
                    inputService.requestInsertOne(player, holder, clickedInventory, event.getSlot(), currentItem);
                    return;
                }
            }
        }

        return;
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

        Player player = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : null;
        if (player == null) {
            event.setCancelled(true);
            return;
        }

        InventoryView view = event.getView();
        ForgeMenuViewResolver.ResolvedView resolved = viewResolver.resolve(player, view);

        if (resolved.getStatus() == ForgeMenuViewResolver.Status.NOT_FORGE) {
            return;
        }

        if (resolved.getStatus() == ForgeMenuViewResolver.Status.STALE) {
            event.setCancelled(true);
            inputService.requestCloseStaleView(player, resolved.getHolder());
            return;
        }

        int topSize = resolved.getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
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

        Player player = event.getPlayer() instanceof Player ? (Player) event.getPlayer() : null;
        if (player == null) {
            return;
        }

        ForgeInventoryHolder holder = (ForgeInventoryHolder) genericHolder;
        inputService.handleInventoryClose(player, holder);
    }

}
