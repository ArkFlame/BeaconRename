package com.arkflame.flameforge.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class TierCost {
    private final CostMode mode;
    private final BigDecimal xpCost;
    private final BigDecimal moneyCost;

    private TierCost(CostMode mode, BigDecimal xpCost, BigDecimal moneyCost) {
        this.mode = Objects.requireNonNull(mode);
        this.xpCost = xpCost;
        this.moneyCost = moneyCost;
    }

    public static TierCost of(CostMode mode, BigDecimal xpCost, BigDecimal moneyCost) {
        if (xpCost != null && xpCost.scale() > 6) {
            throw new IllegalArgumentException("xpCost scale cannot exceed 6");
        }
        if (moneyCost != null && moneyCost.scale() > 6) {
            throw new IllegalArgumentException("moneyCost scale cannot exceed 6");
        }
        return new TierCost(mode, xpCost, moneyCost);
    }

    public static TierCost xpOnly(BigDecimal xpCost) {
        return new TierCost(CostMode.XP_ONLY, Objects.requireNonNull(xpCost), null);
    }

    public static TierCost moneyOnly(BigDecimal moneyCost) {
        return new TierCost(CostMode.MONEY_ONLY, null, Objects.requireNonNull(moneyCost));
    }

    public static TierCost xpAndMoney(BigDecimal xpCost, BigDecimal moneyCost) {
        return new TierCost(CostMode.XP_AND_MONEY, Objects.requireNonNull(xpCost), Objects.requireNonNull(moneyCost));
    }

    public CostMode getMode() { return mode; }
    public BigDecimal getXpCost() { return xpCost; }
    public BigDecimal getMoneyCost() { return moneyCost; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TierCost)) return false;
        TierCost that = (TierCost) o;
        return mode == that.mode &&
               Objects.equals(xpCost, that.xpCost) &&
               Objects.equals(moneyCost, that.moneyCost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, xpCost, moneyCost);
    }

    @Override
    public String toString() {
        return "TierCost{mode=" + mode + ", xpCost=" + xpCost + ", moneyCost=" + moneyCost + "}";
    }
}
