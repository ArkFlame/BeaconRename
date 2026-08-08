package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.model.TierRequirements;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CostQuote {

    private final TierRequirements requirements;
    private final boolean ready;
    private final boolean economyAvailable;
    private final int xpRequired;
    private final int xpAvailable;
    private final BigDecimal moneyRequired;
    private final BigDecimal moneyAvailable;
    private final List<ItemRequirementQuote> itemQuotes;
    private final List<String> missingReasonKeys;

    private CostQuote(TierRequirements requirements, boolean ready, boolean economyAvailable,
                       int xpRequired, int xpAvailable, BigDecimal moneyRequired, BigDecimal moneyAvailable,
                       List<ItemRequirementQuote> itemQuotes, List<String> missingReasonKeys) {
        this.requirements = requirements;
        this.ready = ready;
        this.economyAvailable = economyAvailable;
        this.xpRequired = xpRequired;
        this.xpAvailable = xpAvailable;
        this.moneyRequired = moneyRequired;
        this.moneyAvailable = moneyAvailable;
        this.itemQuotes = itemQuotes != null ? Collections.unmodifiableList(itemQuotes) : Collections.emptyList();
        this.missingReasonKeys = missingReasonKeys != null ? Collections.unmodifiableList(missingReasonKeys) : Collections.emptyList();
    }

    public static CostQuote of(TierRequirements requirements, boolean ready, boolean economyAvailable,
                                int xpRequired, int xpAvailable, BigDecimal moneyRequired, BigDecimal moneyAvailable,
                                List<ItemRequirementQuote> itemQuotes, List<String> missingReasonKeys) {
        return new CostQuote(requirements, ready, economyAvailable, xpRequired, xpAvailable,
                moneyRequired, moneyAvailable, itemQuotes, missingReasonKeys);
    }

    public static CostQuote zero() {
        return new CostQuote(null, true, true, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                Collections.emptyList(), Collections.emptyList());
    }

    public TierRequirements getRequirements() { return requirements; }
    public boolean isReady() { return ready; }
    public boolean isEconomyAvailable() { return economyAvailable; }
    public int getXpRequired() { return xpRequired; }
    public int getXpAvailable() { return xpAvailable; }
    public BigDecimal getMoneyRequired() { return moneyRequired; }
    public BigDecimal getMoneyAvailable() { return moneyAvailable; }
    public List<ItemRequirementQuote> getItemQuotes() { return itemQuotes; }
    public List<String> getMissingReasonKeys() { return missingReasonKeys; }

    public boolean isAffordable() {
        return ready;
    }

    public static final class ItemRequirementQuote {
        private final List<String> materialCandidates;
        private final int amount;
        private final int amountAvailable;
        private final String displayName;
        private final boolean available;
        private final int sourceSlot;
        private final ItemStack originalClone;
        private final int amountToRemove;

        public ItemRequirementQuote(List<String> materialCandidates, int amount, int amountAvailable, String displayName, boolean available,
                                    int sourceSlot, ItemStack originalClone, int amountToRemove) {
            this.materialCandidates = Collections.unmodifiableList(new java.util.ArrayList<>(materialCandidates));
            this.amount = amount;
            this.amountAvailable = amountAvailable;
            this.displayName = displayName;
            this.available = available;
            this.sourceSlot = sourceSlot;
            this.originalClone = originalClone != null ? originalClone.clone() : null;
            this.amountToRemove = amountToRemove;
        }

        public static ItemRequirementQuote available(List<String> materialCandidates, int amount, int amountAvailable, String displayName) {
            return new ItemRequirementQuote(materialCandidates, amount, amountAvailable, displayName, true, -1, null, 0);
        }

        public static ItemRequirementQuote unavailable(List<String> materialCandidates, int amount, int amountAvailable, String displayName) {
            return new ItemRequirementQuote(materialCandidates, amount, amountAvailable, displayName, false, -1, null, 0);
        }

        public List<String> getMaterialCandidates() { return materialCandidates; }
        public int getAmount() { return amount; }
        public int getAmountAvailable() { return amountAvailable; }
        public String getDisplayName() { return displayName; }
        public boolean isAvailable() { return available; }
        public int getSourceSlot() { return sourceSlot; }
        public ItemStack getOriginalClone() { return originalClone != null ? originalClone.clone() : null; }
        public int getAmountToRemove() { return amountToRemove; }
    }
}
