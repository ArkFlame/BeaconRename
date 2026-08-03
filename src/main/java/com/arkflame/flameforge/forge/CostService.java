package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.hook.EconomyService;
import com.arkflame.flameforge.model.TierRequirements;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CostService {

    private static final String BYPASS_PERMISSION = "flameforge.bypass.cost";
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final JavaPlugin plugin;
    private final EconomyService economyService;

    public CostService(JavaPlugin plugin, EconomyService economyService) {
        this.plugin = Objects.requireNonNull(plugin);
        this.economyService = Objects.requireNonNull(economyService);
    }

    public CostQuote quote(Player player, TierRequirements requirements) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(requirements);

        if (hasBypassPermission(player)) {
            return CostQuote.zero();
        }

        final boolean economyAvailable = economyService.available();
        final List<String> missingReasonKeys = new ArrayList<>();
        final List<CostQuote.ItemRequirementQuote> itemQuotes = new ArrayList<>();

        final TierRequirements.XpRequirement xpReq = requirements.getXp();
        final int xpRequired = xpReq.isEnabled() ? xpReq.getLevel() : 0;
        final int xpAvailable = player.getLevel();
        if (xpReq.isEnabled() && xpAvailable < xpRequired) {
            missingReasonKeys.add("forge.error.insufficient_xp");
        }

        final TierRequirements.MoneyRequirement moneyReq = requirements.getMoney();
        final BigDecimal moneyRequired = moneyReq.isEnabled() ? moneyReq.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING) : BigDecimal.ZERO;
        final BigDecimal moneyAvailable = economyAvailable ? economyService.balance(player) : BigDecimal.ZERO;
        if (moneyReq.isEnabled() && moneyAvailable.compareTo(moneyRequired) < 0) {
            missingReasonKeys.add("forge.error.insufficient_money");
        }

        final TierRequirements.ItemsRequirement itemsReq = requirements.getItems();
        if (itemsReq.isEnabled()) {
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                int amountNeeded = itemReq.getAmount();
                int amountHave = countItems(player, itemReq.getMaterialCandidates());
                boolean available = amountHave >= amountNeeded;
                itemQuotes.add(new CostQuote.ItemRequirementQuote(
                        itemReq.getMaterialCandidates(), amountNeeded, itemReq.getDisplayName(), available));
                if (!available) {
                    missingReasonKeys.add("forge.error.insufficient_items");
                    break;
                }
            }
        }

        final boolean ready = missingReasonKeys.isEmpty();
        return CostQuote.of(requirements, ready, economyAvailable, xpRequired, xpAvailable,
                moneyRequired, moneyAvailable, itemQuotes, missingReasonKeys);
    }

    public ChargeReceipt charge(Player player, TierRequirements requirements, List<ItemStack> inputItems) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(requirements);
        Objects.requireNonNull(inputItems);

        if (hasBypassPermission(player)) {
            return ChargeReceipt.zero();
        }

        final TierRequirements.Combine combine = requirements.getCombine();
        final List<ChargeReceipt.RemovedItemStack> removedItems = new ArrayList<>();

        if (combine == TierRequirements.Combine.ALL) {
            return chargeAll(player, requirements, inputItems, removedItems);
        } else {
            return chargeAny(player, requirements, inputItems, removedItems);
        }
    }

    private ChargeReceipt chargeAll(Player player, TierRequirements requirements,
                                    List<ItemStack> inputItems, List<ChargeReceipt.RemovedItemStack> removedItems) {
        final TierRequirements.XpRequirement xpReq = requirements.getXp();
        final int xpRequired = xpReq.isEnabled() ? xpReq.getLevel() : 0;
        final TierRequirements.MoneyRequirement moneyReq = requirements.getMoney();
        final BigDecimal moneyRequired = moneyReq.isEnabled() ? moneyReq.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING) : BigDecimal.ZERO;
        final TierRequirements.ItemsRequirement itemsReq = requirements.getItems();

        if (xpReq.isEnabled() && player.getLevel() < xpRequired) {
            return ChargeReceipt.failure("forge.error.insufficient_xp");
        }
        if (moneyReq.isEnabled() && economyService.balance(player).compareTo(moneyRequired) < 0) {
            return ChargeReceipt.failure("forge.error.insufficient_money");
        }
        if (itemsReq.isEnabled()) {
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                int amountHave = countItems(player, itemReq.getMaterialCandidates());
                if (amountHave < itemReq.getAmount()) {
                    return ChargeReceipt.failure("forge.error.insufficient_items");
                }
            }
        }

        if (xpReq.isEnabled()) {
            deductXp(player, xpRequired);
        }

        if (moneyReq.isEnabled()) {
            if (!economyService.withdraw(player, moneyRequired)) {
                refundXp(player, xpRequired);
                return ChargeReceipt.failure("forge.error.money_withdrawal_failed");
            }
        }

        if (itemsReq.isEnabled()) {
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                int removed = removeRequiredItems(player, itemReq.getMaterialCandidates(), itemReq.getAmount(), removedItems);
                if (removed < itemReq.getAmount()) {
                    refundRemovedItems(player, removedItems);
                    refundMoney(player, moneyRequired);
                    refundXp(player, xpRequired);
                    return ChargeReceipt.failure("forge.error.insufficient_items");
                }
            }
        }

        return ChargeReceipt.success(xpRequired, moneyRequired, new ArrayList<>(removedItems));
    }

    private ChargeReceipt chargeAny(Player player, TierRequirements requirements,
                                     List<ItemStack> inputItems, List<ChargeReceipt.RemovedItemStack> removedItems) {
        final TierRequirements.XpRequirement xpReq = requirements.getXp();
        final TierRequirements.MoneyRequirement moneyReq = requirements.getMoney();
        final TierRequirements.ItemsRequirement itemsReq = requirements.getItems();

        boolean anyEnabled = xpReq.isEnabled() || moneyReq.isEnabled() || itemsReq.isEnabled();
        if (!anyEnabled) {
            return ChargeReceipt.zero();
        }

        if (xpReq.isEnabled()) {
            final int xpRequired = xpReq.getLevel();
            if (player.getLevel() >= xpRequired) {
                if (deductXp(player, xpRequired) == xpRequired) {
                    return ChargeReceipt.success(xpRequired, BigDecimal.ZERO, new ArrayList<>(removedItems));
                }
            }
        }

        if (moneyReq.isEnabled()) {
            final BigDecimal moneyRequired = moneyReq.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING);
            if (economyService.balance(player).compareTo(moneyRequired) >= 0) {
                if (economyService.withdraw(player, moneyRequired)) {
                    return ChargeReceipt.success(0, moneyRequired, new ArrayList<>(removedItems));
                }
            }
        }

        if (itemsReq.isEnabled()) {
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                int amountHave = countItems(player, itemReq.getMaterialCandidates());
                if (amountHave < itemReq.getAmount()) {
                    return ChargeReceipt.failure("forge.error.insufficient_items");
                }
            }
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                removeRequiredItems(player, itemReq.getMaterialCandidates(), itemReq.getAmount(), removedItems);
            }
            return ChargeReceipt.success(0, BigDecimal.ZERO, new ArrayList<>(removedItems));
        }

        return ChargeReceipt.failure("forge.error.no_affordable_option");
    }

    public ChargeReceipt refund(Player player, ChargeReceipt receipt) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(receipt);

        if (!receipt.isSuccess()) {
            return receipt;
        }

        if (receipt.isRefunded()) {
            return receipt;
        }

        if (!receipt.getRemovedItems().isEmpty()) {
            refundRemovedItems(player, receipt.getRemovedItems());
        }

        if (receipt.getMoneyCharged().compareTo(BigDecimal.ZERO) > 0) {
            economyService.deposit(player, receipt.getMoneyCharged());
        }

        if (receipt.getXpCharged() > 0) {
            refundXp(player, receipt.getXpCharged());
        }

        receipt.markRefunded();
        return receipt;
    }

    private boolean hasBypassPermission(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    private int countItems(Player player, List<String> materialCandidates) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                String materialName = item.getType().name();
                for (String candidate : materialCandidates) {
                    if (materialName.equalsIgnoreCase(candidate)) {
                        total += item.getAmount();
                        break;
                    }
                }
            }
        }
        return total;
    }

    private int removeRequiredItems(Player player, List<String> materialCandidates, int amountNeeded,
                                    List<ChargeReceipt.RemovedItemStack> removedItems) {
        int remaining = amountNeeded;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                String materialName = item.getType().name();
                for (String candidate : materialCandidates) {
                    if (materialName.equalsIgnoreCase(candidate)) {
                        int toRemove = Math.min(remaining, item.getAmount());
                        removedItems.add(new ChargeReceipt.RemovedItemStack(i, item.clone()));
                        item.setAmount(item.getAmount() - toRemove);
                        remaining -= toRemove;
                        break;
                    }
                }
            }
        }
        return amountNeeded - remaining;
    }

    private void refundRemovedItems(Player player, List<ChargeReceipt.RemovedItemStack> removedItems) {
        for (ChargeReceipt.RemovedItemStack removed : removedItems) {
            ItemStack clone = removed.getClonedStack();
            if (clone != null) {
                player.getInventory().setItem(removed.getSourceSlot(), clone);
            }
        }
    }

    private int deductXp(Player player, int levels) {
        if (levels <= 0) {
            return 0;
        }
        int currentLevel = player.getLevel();
        int newLevel = Math.max(0, currentLevel - levels);
        player.setLevel(newLevel);
        return levels;
    }

    private void refundXp(Player player, int levels) {
        if (levels <= 0) {
            return;
        }
        int currentLevel = player.getLevel();
        player.setLevel(currentLevel + levels);
    }

    private void refundMoney(Player player, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        economyService.deposit(player, amount);
    }
}
