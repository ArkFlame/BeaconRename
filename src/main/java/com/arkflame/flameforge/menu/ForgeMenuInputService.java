package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.forge.ForgeItemPolicy;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.MessageService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ForgeMenuInputService {
    private final ForgeMenuRegistry registry;
    private final ForgeMenuViewResolver viewResolver;
    private final ForgeMenuService menuService;
    private final ForgeItemPolicy itemPolicy;
    private final ForgeMenuSettlementService settlementService;
    private final SchedulerBridge scheduler;
    private final MessageService messageService;

    public ForgeMenuInputService(ForgeMenuRegistry registry, ForgeMenuViewResolver viewResolver,
                                 ForgeMenuService menuService, ForgeItemPolicy itemPolicy,
                                 ForgeMenuSettlementService settlementService, SchedulerBridge scheduler,
                                 MessageService messageService) {
        this.registry = Objects.requireNonNull(registry);
        this.viewResolver = Objects.requireNonNull(viewResolver);
        this.menuService = Objects.requireNonNull(menuService);
        this.itemPolicy = Objects.requireNonNull(itemPolicy);
        this.settlementService = Objects.requireNonNull(settlementService);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.messageService = Objects.requireNonNull(messageService);
    }

    public void requestInsertOne(Player player, ForgeInventoryHolder holder, Inventory sourceInventory,
                                int sourceSlot, ItemStack expectedItem) {
        if (player == null || sourceInventory == null || expectedItem == null) {
            return;
        }
        if (sourceSlot < 0 || sourceSlot >= sourceInventory.getSize()) {
            return;
        }
        ItemStack expectedClone = expectedItem.clone();
        if (expectedClone.getType() == Material.AIR) {
            return;
        }

        UUID playerId = player.getUniqueId();
        scheduler.runEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!viewResolver.isStillCurrent(player, holder)) {
                return;
            }
            Inventory currentViewBottom = player.getOpenInventory().getBottomInventory();
            if (currentViewBottom != sourceInventory) {
                return;
            }
            if (sourceSlot >= sourceInventory.getSize()) {
                return;
            }

            Optional<ForgeMenuContext> contextOpt = registry.getCurrent(playerId, holder.getMenuId());
            if (!contextOpt.isPresent()) {
                return;
            }
            ForgeMenuContext context = contextOpt.get();
            if (!context.isOpen()) {
                return;
            }

            ItemStack sourceItem = sourceInventory.getItem(sourceSlot);
            if (sourceItem == null || sourceItem.getType() == Material.AIR) {
                return;
            }
            if (!sourceItem.isSimilar(expectedClone)) {
                messageService.send(player, "menu.stale-click");
                return;
            }
            if (context.peekInput().isPresent()) {
                messageService.send(player, "menu.input-occupied");
                return;
            }

            ForgeItemPolicy.PolicyResult policyResult = itemPolicy.checkItem(player, context.getSession(), sourceItem);
            if (!policyResult.isAllowed()) {
                messageService.send(player, policyResult.getMessageKey());
                return;
            }

            ItemStack oneItem = sourceItem.clone();
            oneItem.setAmount(1);
            if (!context.tryInsert(oneItem)) {
                messageService.send(player, "menu.input-occupied");
                return;
            }

            ItemStack remainder = sourceItem.clone();
            remainder.setAmount(sourceItem.getAmount() - 1);
            sourceInventory.setItem(sourceSlot, remainder);

            messageService.send(player, "menu.item-inserted");
            ForgeMenuService.MenuResult rerenderResult = menuService.rerender(player);
            if (!rerenderResult.isOpened()) {
                registry.removeIfCurrent(playerId, holder.getMenuId());
                settlementService.settleOnlineOrQueue(context, player);
                player.closeInventory();
                messageService.send(player, "open.menu-open-failed",
                        MessageArguments.create()
                                .string("station_id", holder.getStationId())
                                .string("reason", rerenderResult.getReason())
                                .string("reference", rerenderResult.getReference()));
            }
        }, () -> {
        });
    }

    public void requestReturnInput(Player player, ForgeInventoryHolder holder) {
        if (player == null || holder == null) {
            return;
        }
        UUID playerId = player.getUniqueId();

        scheduler.runEntity(player, () -> {
            if (!viewResolver.isStillCurrent(player, holder)) {
                return;
            }
            Optional<ForgeMenuContext> contextOpt = registry.getCurrent(playerId, holder.getMenuId());
            if (!contextOpt.isPresent()) {
                return;
            }
            ForgeMenuContext context = contextOpt.get();
            Optional<ItemStack> extracted = context.extractInput();
            if (extracted.isPresent()) {
                settlementService.settleOnlineOrQueue(context, player);
            }
            ForgeMenuService.MenuResult rerenderResult = menuService.rerender(player);
            if (!rerenderResult.isOpened()) {
                registry.removeIfCurrent(playerId, holder.getMenuId());
                settlementService.settleOnlineOrQueue(context, player);
                player.closeInventory();
                messageService.send(player, "open.menu-open-failed",
                        MessageArguments.create()
                                .string("station_id", holder.getStationId())
                                .string("reason", rerenderResult.getReason())
                                .string("reference", rerenderResult.getReference()));
            }
        }, () -> {
        });
    }

    public void requestCloseStaleView(Player player, ForgeInventoryHolder holder) {
        if (player == null || holder == null) {
            return;
        }
        scheduler.runEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            InventoryView view = player.getOpenInventory();
            if (view == null) {
                return;
            }
            Inventory topInventory = view.getTopInventory();
            if (!(topInventory.getHolder() instanceof ForgeInventoryHolder)) {
                return;
            }
            ForgeInventoryHolder topHolder = (ForgeInventoryHolder) topInventory.getHolder();
            if (topHolder.getMenuId().equals(holder.getMenuId())) {
                player.closeInventory();
            }
        }, () -> {
        });
    }

    public void handleInventoryClose(Player player, ForgeInventoryHolder holder) {
        if (player == null || holder == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        UUID menuId = holder.getMenuId();
        Optional<ForgeMenuContext> removed = registry.removeIfCurrent(playerId, menuId);
        if (!removed.isPresent()) {
            return;
        }
        ForgeMenuContext context = removed.get();
        scheduler.runEntity(player, () -> {
            if (player.isOnline()) {
                settlementService.settleOnlineOrQueue(context, player);
            } else {
                settlementService.settleOffline(context);
            }
        }, () -> {
        });
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        Optional<ForgeMenuContext> removed = registry.remove(player.getUniqueId());
        removed.ifPresent(settlementService::settleOffline);
    }

    public void shutdown() {
        for (ForgeMenuContext context : registry.drain()) {
            settlementService.settleOffline(context);
        }
    }
}
