package com.arkflame.flameforge.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TierRequirements {
    public enum Combine {
        ALL,
        ANY
    }

    private final Combine combine;
    private final XpRequirement xp;
    private final MoneyRequirement money;
    private final ItemsRequirement items;

    public TierRequirements(Combine combine, XpRequirement xp, MoneyRequirement money, ItemsRequirement items) {
        this.combine = combine != null ? combine : Combine.ALL;
        this.xp = xp != null ? xp : new XpRequirement(false, 0);
        this.money = money != null ? money : new MoneyRequirement(false, BigDecimal.ZERO);
        this.items = items != null ? items : new ItemsRequirement(false, Collections.emptyList());
    }

    public Combine getCombine() { return combine; }
    public XpRequirement getXp() { return xp; }
    public MoneyRequirement getMoney() { return money; }
    public ItemsRequirement getItems() { return items; }

    public static final class XpRequirement {
        private final boolean enabled;
        private final int level;

        public XpRequirement(boolean enabled, int level) {
            this.enabled = enabled;
            this.level = level;
        }

        public boolean isEnabled() { return enabled; }
        public int getLevel() { return level; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof XpRequirement)) return false;
            XpRequirement that = (XpRequirement) o;
            return enabled == that.enabled && level == that.level;
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, level);
        }

        @Override
        public String toString() {
            return "XpRequirement{enabled=" + enabled + ", level=" + level + "}";
        }
    }

    public static final class MoneyRequirement {
        private final boolean enabled;
        private final BigDecimal amount;

        public MoneyRequirement(boolean enabled, BigDecimal amount) {
            this.enabled = enabled;
            this.amount = amount != null ? amount : BigDecimal.ZERO;
        }

        public boolean isEnabled() { return enabled; }
        public BigDecimal getAmount() { return amount; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MoneyRequirement)) return false;
            MoneyRequirement that = (MoneyRequirement) o;
            return enabled == that.enabled && Objects.equals(amount, that.amount);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, amount);
        }

        @Override
        public String toString() {
            return "MoneyRequirement{enabled=" + enabled + ", amount=" + amount + "}";
        }
    }

    public static final class ItemsRequirement {
        private final boolean enabled;
        private final List<ItemRequirement> items;

        public ItemsRequirement(boolean enabled, List<ItemRequirement> items) {
            this.enabled = enabled;
            this.items = items != null ? Collections.unmodifiableList(new java.util.ArrayList<>(items)) : Collections.emptyList();
        }

        public boolean isEnabled() { return enabled; }
        public List<ItemRequirement> getItems() { return items; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemsRequirement)) return false;
            ItemsRequirement that = (ItemsRequirement) o;
            return enabled == that.enabled && Objects.equals(items, that.items);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, items);
        }

        @Override
        public String toString() {
            return "ItemsRequirement{enabled=" + enabled + ", items=" + items + "}";
        }
    }

    public static final class ItemRequirement {
        private final List<String> materialCandidates;
        private final int amount;
        private final String displayName;

        public ItemRequirement(List<String> materialCandidates, int amount, String displayName) {
            if (materialCandidates == null || materialCandidates.isEmpty()) {
                throw new IllegalArgumentException("materialCandidates cannot be null or empty");
            }
            this.materialCandidates = Collections.unmodifiableList(new java.util.ArrayList<>(materialCandidates));
            this.amount = amount;
            this.displayName = displayName;
        }

        public List<String> getMaterialCandidates() { return materialCandidates; }
        public int getAmount() { return amount; }
        public String getDisplayName() { return displayName; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemRequirement)) return false;
            ItemRequirement that = (ItemRequirement) o;
            return amount == that.amount &&
                   Objects.equals(materialCandidates, that.materialCandidates) &&
                   Objects.equals(displayName, that.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(materialCandidates, amount, displayName);
        }

        @Override
        public String toString() {
            return "ItemRequirement{materialCandidates=" + materialCandidates +
                   ", amount=" + amount + ", displayName=" + displayName + "}";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TierRequirements)) return false;
        TierRequirements that = (TierRequirements) o;
        return combine == that.combine &&
               Objects.equals(xp, that.xp) &&
               Objects.equals(money, that.money) &&
               Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(combine, xp, money, items);
    }

    @Override
    public String toString() {
        return "TierRequirements{combine=" + combine + ", xp=" + xp +
               ", money=" + money + ", items=" + items + "}";
    }
}
