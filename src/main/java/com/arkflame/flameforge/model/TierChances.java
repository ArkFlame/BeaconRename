package com.arkflame.flameforge.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TierChances {
    private final BigDecimal successPercent;
    private final BigDecimal breakPercent;
    private final BigDecimal cursePercent;
    private final Map<String, BigDecimal> variantPercents;

    public TierChances(BigDecimal successPercent, BigDecimal breakPercent, BigDecimal cursePercent) {
        this(successPercent, breakPercent, cursePercent, Collections.emptyMap());
    }

    public TierChances(BigDecimal successPercent, BigDecimal breakPercent, BigDecimal cursePercent,
                       Map<String, BigDecimal> variantPercents) {
        this.successPercent = successPercent != null ? successPercent : BigDecimal.ZERO;
        this.breakPercent = breakPercent != null ? breakPercent : BigDecimal.ZERO;
        this.cursePercent = cursePercent != null ? cursePercent : BigDecimal.ZERO;
        this.variantPercents = variantPercents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(variantPercents))
                : Collections.emptyMap();
    }

    public TierChances(double successPercent, double breakPercent, double cursePercent) {
        this(BigDecimal.valueOf(successPercent), BigDecimal.valueOf(breakPercent), BigDecimal.valueOf(cursePercent));
    }

    public BigDecimal getSuccessPercent() { return successPercent; }
    public BigDecimal getBreakPercent() { return breakPercent; }
    public BigDecimal getCursePercent() { return cursePercent; }
    public Map<String, BigDecimal> getVariantPercents() { return variantPercents; }

    public BigDecimal getVariantPercent(String variantId) {
        return variantId != null ? variantPercents.get(variantId) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TierChances)) return false;
        TierChances that = (TierChances) o;
        return Objects.equals(successPercent, that.successPercent) &&
               Objects.equals(breakPercent, that.breakPercent) &&
               Objects.equals(cursePercent, that.cursePercent) &&
               Objects.equals(variantPercents, that.variantPercents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successPercent, breakPercent, cursePercent, variantPercents);
    }

    @Override
    public String toString() {
        return "TierChances{successPercent=" + successPercent +
               ", breakPercent=" + breakPercent +
               ", cursePercent=" + cursePercent +
               ", variantPercents=" + variantPercents + "}";
    }
}
