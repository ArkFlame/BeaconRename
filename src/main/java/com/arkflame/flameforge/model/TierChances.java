package com.arkflame.flameforge.model;

import java.util.Objects;

public final class TierChances {
    private final double successPercent;
    private final double breakPercent;
    private final double cursePercent;

    public TierChances(double successPercent, double breakPercent, double cursePercent) {
        this.successPercent = successPercent;
        this.breakPercent = breakPercent;
        this.cursePercent = cursePercent;
    }

    public double getSuccessPercent() { return successPercent; }
    public double getBreakPercent() { return breakPercent; }
    public double getCursePercent() { return cursePercent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TierChances)) return false;
        TierChances that = (TierChances) o;
        return Double.compare(that.successPercent, successPercent) == 0 &&
               Double.compare(that.breakPercent, breakPercent) == 0 &&
               Double.compare(that.cursePercent, cursePercent) == 0;
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
