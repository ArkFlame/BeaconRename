package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.forge.ForgePlan;
import com.arkflame.flameforge.forge.ForgePlanResult;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
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
    private final ForgePowerService forgePowerService;

    public ForgeMenuForgeService(ForgeMenuRegistry registry, ForgeMenuViewResolver viewResolver,
                                ForgeService forgeService, ForgeMenuSettlementService settlementService,
                                ForgeMenuService menuService, SchedulerBridge scheduler,
                                MessageService messageService, Logger logger,
                                ForgePowerService forgePowerService) {
        this.registry = registry;
        this.viewResolver = viewResolver;
        this.forgeService = forgeService;
        this.settlementService = settlementService;
        this.menuService = menuService;
        this.scheduler = scheduler;
        this.messageService = messageService;
        this.logger = logger;
        this.forgePowerService = forgePowerService;
    }

    public void requestConfirm(Player player, ForgeInventoryHolder holder) {
        if (player == null || holder == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        UUID menuId = holder.getMenuId();

        ForgeMenuContext removedContext = null;
        ItemStack claimedItem = null;
        ItemStack claimedSafetyCopy = null;
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
            claimedSafetyCopy = claimedItem.clone();

            player.closeInventory();
            submitted = true;

            forgeService.confirmAndExecute(player, session, claimedItem, plan, resolution -> {
                scheduler.runEntity(player,
                    () -> {
                        if (!resolution.isSuccess()) {
                            String errorReason = resolution.getErrorMessage();
                            messageService.send(player, "forge.confirm.failed",
                                MessageArguments.create().string("reason",
                                    errorReason != null ? errorReason : "unknown"));
                            return;
                        }

                        ForgeOutcomeCategory category = resolution.getCategory();
                        if (category == ForgeOutcomeCategory.SUCCESS) {
                            forgePowerService.refreshPassivePowers(player);
                            messageService.send(player, "forge.confirm.success",
                                MessageArguments.create().component("item", itemComponent(resolution.getMutatedItem())));
                        } else if (category == ForgeOutcomeCategory.CURSE) {
                            forgePowerService.refreshPassivePowers(player);
                            messageService.send(player, "forge.confirm.cursed");
                        } else if (category == ForgeOutcomeCategory.BREAK) {
                            forgePowerService.refreshPassivePowers(player);
                            if (resolution.hasItemOutput()) {
                                messageService.send(player, "forge.confirm.fractured",
                                    MessageArguments.create().component("item", itemComponent(resolution.getMutatedItem())));
                            } else {
                                messageService.send(player, "forge.confirm.shattered");
                            }
                        } else {
                            messageService.send(player, "forge.confirm.failed",
                                MessageArguments.create().string("reason", "unknown terminal outcome"));
                        }
                    },
                    () -> {
                    }
                );
            });

        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Unexpected forge submission failure for player " + playerId + ", menu " + menuId, e);
            if (removedContext != null) {
                boolean restored = removedContext.restoreClaimedInputForSettlement(claimedSafetyCopy);
                if (restored) {
                    settlementService.settleOnlineOrQueue(removedContext, player);
                }
                messageService.send(player, "menu.forge-start-failed");
            }
        }
    }

    private Component itemComponent(ItemStack item) {
        if (item == null) {
            return Component.text("item");
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            if (displayName != null && !displayName.trim().isEmpty()) {
                return messageService.getRenderer().fromLegacy(displayName);
            }
        }

        String typeName = item.getType() != null ? item.getType().name() : "ITEM";
        String humanized = typeName.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Component.text(Character.toUpperCase(humanized.charAt(0)) + humanized.substring(1));
    }
}
