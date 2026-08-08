package com.arkflame.flameforge.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class TierChances {
    private final BigDecimal successPercent;
    private final BigDecimal breakPercent;
    private final BigDecimal cursePercent;

    public TierChances(BigDecimal successPercent, BigDecimal breakPercent, BigDecimal cursePercent) {
        this.successPercent = successPercent != null ? successPercent : BigDecimal.ZERO;
        this.breakPercent = breakPercent != null ? breakPercent : BigDecimal.ZERO;
        this.cursePercent = cursePercent != null ? cursePercent : BigDecimal.ZERO;
    }

    public TierChances(double successPercent, double breakPercent, double cursePercent) {
        this(BigDecimal.valueOf(successPercent), BigDecimal.valueOf(breakPercent), BigDecimal.valueOf(cursePercent));
    }

    public BigDecimal getSuccessPercent() { return successPercent; }
    public BigDecimal getBreakPercent() { return breakPercent; }
    public BigDecimal getCursePercent() { return cursePercent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TierChances)) return false;
        TierChances that = (TierChances) o;
        return Objects.equals(successPercent, that.successPercent) &&
               Objects.equals(breakPercent, that.breakPercent) &&
               Objects.equals(cursePercent, that.cursePercent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successPercent, breakPercent, cursePercent);
    }

    @Override
    public String toString() {
        return "TierChances{successPercent=" + successPercent +
               ", breakPercent=" + breakPercent + ", cursePercent=" + cursePercent + "}";
    }
}
