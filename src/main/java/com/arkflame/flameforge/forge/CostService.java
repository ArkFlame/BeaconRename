package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.material.MaterialResolver;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CostService {

    private static final String BYPASS_PERMISSION = "flameforge.bypass.cost";
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final MaterialResolver materialResolver;
    private final DeliveryService deliveryService;

    public CostService(JavaPlugin plugin, EconomyService economyService) {
        this(plugin, economyService, MaterialResolver.getInstance(), null);
    }

    public CostService(JavaPlugin plugin, EconomyService economyService,
                       MaterialResolver materialResolver, DeliveryService deliveryService) {
        this.plugin = Objects.requireNonNull(plugin);
        this.economyService = Objects.requireNonNull(economyService);
        this.materialResolver = materialResolver != null ? materialResolver : MaterialResolver.getInstance();
        this.deliveryService = deliveryService;
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

        final TierRequirements.MoneyRequirement moneyReq = requirements.getMoney();
        final BigDecimal moneyRequired = moneyReq.isEnabled() ? moneyReq.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING) : BigDecimal.ZERO;
        final BigDecimal moneyAvailable = economyAvailable ? economyService.balance(player) : BigDecimal.ZERO;

        final TierRequirements.ItemsRequirement itemsReq = requirements.getItems();
        if (itemsReq.isEnabled()) {
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                int amountNeeded = itemReq.getAmount();
                int amountHave = countItems(player, itemReq.getMaterialCandidates());
                boolean available = amountHave >= amountNeeded;
                itemQuotes.add(new CostQuote.ItemRequirementQuote(
                        itemReq.getMaterialCandidates(), amountNeeded, amountHave, itemReq.getDisplayName(), available, -1, null, 0));
            }
        }

        final boolean xpEnabled = xpReq.isEnabled();
        final boolean moneyEnabled = moneyReq.isEnabled();
        final boolean itemsEnabled = itemsReq.isEnabled();
        final int enabledCount = (xpEnabled ? 1 : 0) + (moneyEnabled ? 1 : 0) + (itemsEnabled ? 1 : 0);

        final boolean xpSatisfied = !xpEnabled || xpAvailable >= xpRequired;
        final boolean moneySatisfied = !moneyEnabled || (economyAvailable && moneyAvailable.compareTo(moneyRequired) >= 0);
        final boolean itemsSatisfied = !itemsEnabled || itemQuotes.stream().allMatch(CostQuote.ItemRequirementQuote::isAvailable);

        final TierRequirements.Combine combine = requirements.getCombine();
        final boolean ready;
        if (enabledCount == 0) {
            ready = true;
        } else if (combine == TierRequirements.Combine.ALL) {
            ready = xpSatisfied && moneySatisfied && itemsSatisfied;
        } else {
            ready = (xpEnabled && xpSatisfied)
                 || (moneyEnabled && moneySatisfied)
                 || (itemsEnabled && itemsSatisfied);
        }

        if (combine == TierRequirements.Combine.ALL) {
            if (xpEnabled && !xpSatisfied) {
                missingReasonKeys.add("forge.error.insufficient_xp");
            }
            if (moneyEnabled && !moneySatisfied) {
                missingReasonKeys.add("forge.error.insufficient_money");
            }
            if (itemsEnabled && !itemsSatisfied) {
                missingReasonKeys.add("forge.error.insufficient_items");
            }
        } else {
            if (ready) {
                // empty missing reasons
            } else {
                if (xpEnabled && !xpSatisfied) {
                    missingReasonKeys.add("forge.error.insufficient_xp");
                }
                if (moneyEnabled && !moneySatisfied) {
                    missingReasonKeys.add("forge.error.insufficient_money");
                }
                if (itemsEnabled && !itemsSatisfied) {
                    missingReasonKeys.add("forge.error.insufficient_items");
                }
            }
        }

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

        final Set<Integer> inputSlots = buildInputSlotSet(player, inputItems);
        final TierRequirements.Combine combine = requirements.getCombine();
        final List<ChargeReceipt.RemovedItemStack> removedItems = new ArrayList<>();

        if (combine == TierRequirements.Combine.ALL) {
            return chargeAll(player, requirements, inputSlots, removedItems);
        } else {
            return chargeAny(player, requirements, inputSlots, removedItems);
        }
    }

    private Set<Integer> buildInputSlotSet(Player player, List<ItemStack> inputItems) {
        Set<Integer> slots = new HashSet<>();
        if (inputItems == null || inputItems.isEmpty()) {
            return slots;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack input : inputItems) {
            if (input == null || input.getType() == Material.AIR) {
                continue;
            }
            for (int i = 0; i < contents.length; i++) {
                ItemStack invItem = contents[i];
                if (invItem != null && invItem.isSimilar(input)) {
                    slots.add(i);
                    break;
                }
            }
        }
        return slots;
    }

    private ChargeReceipt chargeAll(Player player, TierRequirements requirements,
                                    Set<Integer> inputSlots, List<ChargeReceipt.RemovedItemStack> removedItems) {
        final TierRequirements.XpRequirement xpReq = requirements.getXp();
        final int xpRequired = xpReq.isEnabled() ? xpReq.getLevel() : 0;
        final TierRequirements.MoneyRequirement moneyReq = requirements.getMoney();
        final BigDecimal moneyRequired = moneyReq.isEnabled() ? moneyReq.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING) : BigDecimal.ZERO;
        final TierRequirements.ItemsRequirement itemsReq = requirements.getItems();

        if (xpReq.isEnabled() && player.getLevel() < xpRequired) {
            return ChargeReceipt.failure("forge.error.insufficient_xp");
        }
        if (moneyReq.isEnabled() && (!economyService.available() || economyService.balance(player).compareTo(moneyRequired) < 0)) {
            return ChargeReceipt.failure("forge.error.insufficient_money");
        }
        if (itemsReq.isEnabled()) {
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                int amountHave = countItemsExcluding(player, itemReq.getMaterialCandidates(), inputSlots);
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
                int removed = removeRequiredItems(player, itemReq.getMaterialCandidates(), itemReq.getAmount(), inputSlots, removedItems);
                if (removed < itemReq.getAmount()) {
                    refundRemovedItemsWithOverflowHandling(player, removedItems);
                    refundMoney(player, moneyRequired);
                    refundXp(player, xpRequired);
                    return ChargeReceipt.failure("forge.error.insufficient_items");
                }
            }
        }

        return ChargeReceipt.success(xpRequired, moneyRequired, new ArrayList<>(removedItems));
    }

    private ChargeReceipt chargeAny(Player player, TierRequirements requirements,
                                     Set<Integer> inputSlots, List<ChargeReceipt.RemovedItemStack> removedItems) {
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

        if (moneyReq.isEnabled() && economyService.available()) {
            final BigDecimal moneyRequired = moneyReq.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING);
            if (economyService.balance(player).compareTo(moneyRequired) >= 0) {
                if (economyService.withdraw(player, moneyRequired)) {
                    return ChargeReceipt.success(0, moneyRequired, new ArrayList<>(removedItems));
                }
            }
        }

        if (itemsReq.isEnabled()) {
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                int amountHave = countItemsExcluding(player, itemReq.getMaterialCandidates(), inputSlots);
                if (amountHave < itemReq.getAmount()) {
                    return ChargeReceipt.failure("forge.error.insufficient_items");
                }
            }
            for (TierRequirements.ItemRequirement itemReq : itemsReq.getItems()) {
                removeRequiredItems(player, itemReq.getMaterialCandidates(), itemReq.getAmount(), inputSlots, removedItems);
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
            refundRemovedItemsWithOverflowHandling(player, receipt.getRemovedItems());
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
                if (matchesMaterial(item.getType(), materialCandidates)) {
                    total += item.getAmount();
                }
            }
        }
        return total;
    }

    private int countItemsExcluding(Player player, List<String> materialCandidates, Set<Integer> excludeSlots) {
        int total = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (excludeSlots.contains(i)) {
                continue;
            }
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                if (matchesMaterial(item.getType(), materialCandidates)) {
                    total += item.getAmount();
                }
            }
        }
        return total;
    }

    private boolean matchesMaterial(Material type, List<String> materialCandidates) {
        if (type == null || materialCandidates == null) {
            return false;
        }
        String materialName = type.name();
        for (String candidate : materialCandidates) {
            java.util.Optional<Material> resolved = materialResolver.resolve(candidate);
            if (resolved.isPresent() && resolved.get() == type) {
                return true;
            }
        }
        return false;
    }

    private int removeRequiredItems(Player player, List<String> materialCandidates, int amountNeeded,
                                    Set<Integer> excludeSlots, List<ChargeReceipt.RemovedItemStack> removedItems) {
        int remaining = amountNeeded;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (excludeSlots.contains(i)) {
                continue;
            }
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                if (matchesMaterial(item.getType(), materialCandidates)) {
                    int toRemove = Math.min(remaining, item.getAmount());
                    removedItems.add(new ChargeReceipt.RemovedItemStack(i, item.clone(), toRemove));
                    item.setAmount(item.getAmount() - toRemove);
                    remaining -= toRemove;
                }
            }
        }
        return amountNeeded - remaining;
    }

    private void refundRemovedItemsWithOverflowHandling(Player player, List<ChargeReceipt.RemovedItemStack> removedItems) {
        List<ItemStack> overflow = new ArrayList<>();
        for (ChargeReceipt.RemovedItemStack removed : removedItems) {
            ItemStack clone = removed.getClonedStack();
            if (clone == null) {
                continue;
            }
            clone.setAmount(removed.getExactRemovedAmount());
            int slot = removed.getSourceSlot();
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                player.getInventory().setItem(slot, clone);
            } else {
                overflow.add(clone);
            }
        }
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow) {
                java.util.Map<Integer, ItemStack> result = player.getInventory().addItem(item);
                if (!result.isEmpty()) {
                    if (player.isOnline()) {
                        for (ItemStack drop : result.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    } else {
                        queuePendingDeliveryForOfflinePlayer(player, item);
                    }
                }
            }
        }
    }

    private void queuePendingDeliveryForOfflinePlayer(Player player, ItemStack item) {
        if (deliveryService == null) {
            return;
        }
        String deliveryId = "refund_" + player.getUniqueId() + "_" + System.nanoTime();
        deliveryService.queuePendingDelivery(deliveryId, player.getUniqueId(), item, null);
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
