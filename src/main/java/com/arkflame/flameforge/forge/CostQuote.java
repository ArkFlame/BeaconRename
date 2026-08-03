package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.model.TierRequirements;

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
        if (!ready) {
            return false;
        }
        if (xpAvailable < xpRequired) {
            return false;
        }
        if (moneyAvailable.compareTo(moneyRequired) < 0) {
            return false;
        }
        for (ItemRequirementQuote quote : itemQuotes) {
            if (!quote.isAvailable()) {
                return false;
            }
        }
        return true;
    }

    public static final class ItemRequirementQuote {
        private final List<String> materialCandidates;
        private final int amount;
        private final String displayName;
        private final boolean available;

        public ItemRequirementQuote(List<String> materialCandidates, int amount, String displayName, boolean available) {
            this.materialCandidates = Collections.unmodifiableList(new java.util.ArrayList<>(materialCandidates));
            this.amount = amount;
            this.displayName = displayName;
            this.available = available;
        }

        public List<String> getMaterialCandidates() { return materialCandidates; }
        public int getAmount() { return amount; }
        public String getDisplayName() { return displayName; }
        public boolean isAvailable() { return available; }
    }
}
