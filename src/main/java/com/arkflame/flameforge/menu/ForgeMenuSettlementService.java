package com.arkflame.flameforge.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class ForgeMenuSettlementService {
    private final MenuInputReturnService inputReturnService;

    public ForgeMenuSettlementService(MenuInputReturnService inputReturnService) {
        this.inputReturnService = inputReturnService;
    }

    public void settleOnlineOrQueue(ForgeMenuContext context, Player player) {
        if (context == null) {
            return;
        }
        Optional<ItemStack> itemOpt = context.retireAndExtract();
        if (!itemOpt.isPresent()) {
            return;
        }
        ItemStack item = itemOpt.get();
        if (player != null && player.isOnline()) {
            inputReturnService.returnToPlayer(item, player);
        } else {
            inputReturnService.returnToPlayerOffline(item, context.getPlayerId());
        }
    }

    public void settleOffline(ForgeMenuContext context) {
        if (context == null) {
            return;
        }
        Optional<ItemStack> itemOpt = context.retireAndExtract();
        if (!itemOpt.isPresent()) {
            return;
        }
        inputReturnService.returnToPlayerOffline(itemOpt.get(), context.getPlayerId());
    }
}
