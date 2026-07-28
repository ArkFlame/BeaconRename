package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.model.CostMode;

import java.math.BigDecimal;
import java.util.Objects;

public final class CostQuote {

    private final CostMode mode;
    private final BigDecimal xpCost;
    private final BigDecimal moneyCost;
    private final boolean xpAffordable;
    private final boolean moneyAffordable;

    private CostQuote(CostMode mode, BigDecimal xpCost, BigDecimal moneyCost,
                      boolean xpAffordable, boolean moneyAffordable) {
        this.mode = Objects.requireNonNull(mode);
        this.xpCost = xpCost;
        this.moneyCost = moneyCost;
        this.xpAffordable = xpAffordable;
        this.moneyAffordable = moneyAffordable;
    }

    public static CostQuote of(CostMode mode, BigDecimal xpCost, BigDecimal moneyCost,
                               boolean xpAffordable, boolean moneyAffordable) {
        return new CostQuote(mode, xpCost, moneyCost, xpAffordable, moneyAffordable);
    }

    public static CostQuote zero() {
        return new CostQuote(CostMode.XP_ONLY, BigDecimal.ZERO, BigDecimal.ZERO, true, true);
    }

    public CostMode getMode() {
        return mode;
    }

    public BigDecimal getXpCost() {
        return xpCost;
    }

    public BigDecimal getMoneyCost() {
        return moneyCost;
    }

    public boolean isXpAffordable() {
        return xpAffordable;
    }

    public boolean isMoneyAffordable() {
        return moneyAffordable;
    }

    public boolean isAffordable() {
        switch (mode) {
            case XP_ONLY:
                return xpAffordable;
            case MONEY_ONLY:
                return moneyAffordable;
            case XP_AND_MONEY:
                return xpAffordable && moneyAffordable;
            case XP_OR_MONEY:
                return xpAffordable || moneyAffordable;
            default:
                return false;
        }
    }

    public boolean requiresXp() {
        return mode == CostMode.XP_ONLY || mode == CostMode.XP_AND_MONEY || mode == CostMode.XP_OR_MONEY;
    }

    public boolean requiresMoney() {
        return mode == CostMode.MONEY_ONLY || mode == CostMode.XP_AND_MONEY || mode == CostMode.XP_OR_MONEY;
    }
}
