package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.forge.ForgePlan;
import com.arkflame.flameforge.forge.ForgePlanResult;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.text.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class ForgeMenuForgeService {
    private final ForgeMenuRegistry registry;
    private final ForgeMenuViewResolver viewResolver;
    private final ForgeService forgeService;
    private final ForgeMenuSettlementService settlementService;
    private final ForgeMenuService menuService;
    private final SchedulerBridge scheduler;
    private final MessageService messageService;
    private final Logger logger;

    public ForgeMenuForgeService(ForgeMenuRegistry registry, ForgeMenuViewResolver viewResolver,
                                ForgeService forgeService, ForgeMenuSettlementService settlementService,
                                ForgeMenuService menuService, SchedulerBridge scheduler,
                                MessageService messageService, Logger logger) {
        this.registry = registry;
        this.viewResolver = viewResolver;
        this.forgeService = forgeService;
        this.settlementService = settlementService;
        this.menuService = menuService;
        this.scheduler = scheduler;
        this.messageService = messageService;
        this.logger = logger;
    }

    public void requestConfirm(Player player, ForgeInventoryHolder holder) {
        if (player == null || holder == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        UUID menuId = holder.getMenuId();

        ForgeMenuContext removedContext = null;
        ItemStack claimedItem = null;
        boolean submitted = false;

        try {
            if (!player.isOnline()) {
                return;
            }

            if (!viewResolver.isStillCurrent(player, holder)) {
                return;
            }

            Optional<ForgeMenuContext> contextOpt = registry.getCurrent(playerId, menuId);
            if (!contextOpt.isPresent()) {
                return;
            }

            ForgeMenuContext context = contextOpt.get();
            if (!context.isOpen()) {
                messageService.send(player, "menu.forge-already-started");
                return;
            }

            Optional<ItemStack> inputOpt = context.peekInput();
            if (!inputOpt.isPresent()) {
                messageService.send(player, "menu.item-denied.empty");
                return;
            }
            ItemStack input = inputOpt.get().clone();

            PlayerForgeState session = context.getSession();
            ForgePlanResult planResult = forgeService.createPlan(player, session, input);
            if (!planResult.isReady()) {
                String reason = planResult.reasonKey != null ? planResult.reasonKey : "menu.item-denied.no-tier";
                messageService.send(player, reason);
                menuService.rerender(player);
                return;
            }

            ForgePlan plan = planResult.plan;
            if (!plan.isAffordable()) {
                messageService.send(player, "menu.requirements-not-met");
                menuService.rerender(player);
                return;
            }

            Optional<ForgeMenuContext> removed = registry.removeIfCurrent(playerId, menuId);
            if (!removed.isPresent()) {
                return;
            }
            removedContext = removed.get();

            Optional<ItemStack> claimed = context.claimInputForForge();
            if (!claimed.isPresent()) {
                settlementService.settleOnlineOrQueue(removedContext, player);
                player.closeInventory();
                messageService.send(player, "menu.forge-start-failed");
                return;
            }
            claimedItem = claimed.get();

            player.closeInventory();
            submitted = true;

            forgeService.confirmAndExecute(player, session, claimedItem, plan, resolution -> {
                scheduler.runEntity(player,
                    () -> {
                        if (resolution.isSuccess()) {
                            messageService.send(player, "forge.confirm.complete");
                        } else {
                            String errorReason = resolution.getErrorMessage();
                            messageService.send(player, "forge.confirm.failed",
                                Collections.singletonMap("reason", errorReason != null ? errorReason : "unknown"),
                                Collections.emptyMap());
                        }
                    },
                    () -> {
                    }
                );
            });

        } catch (RuntimeException e) {
            logger.warning("Unexpected pre-submission failure for player " + playerId + ", menu " + menuId + ": " + e.getMessage());
            if (claimedItem != null && submitted) {
                Optional<ForgeMenuContext> currentContext = registry.getCurrent(playerId, menuId);
                if (currentContext.isPresent()) {
                    boolean restored = currentContext.get().restoreClaimedInputForSettlement(claimedItem);
                    if (restored) {
                        settlementService.settleOnlineOrQueue(currentContext.get(), player);
                    }
                }
                messageService.send(player, "menu.forge-start-failed");
            } else if (removedContext != null) {
                Optional<ForgeMenuContext> currentContext = registry.getCurrent(playerId, menuId);
                if (currentContext.isPresent()) {
                    settlementService.settleOnlineOrQueue(currentContext.get(), player);
                } else {
                    settlementService.settleOnlineOrQueue(removedContext, player);
                }
            }
        }
    }
}
