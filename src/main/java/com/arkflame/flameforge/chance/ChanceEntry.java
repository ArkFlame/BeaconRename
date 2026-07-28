package com.arkflame.flameforge.chance;

import java.math.BigDecimal;
import java.util.Objects;

public final class ChanceEntry {
    private final String outcomeId;
    private final BigDecimal weight;
    private final long microWeight;
    private final BigDecimal displayPercentage;
    private final int yamlOrder;

    private ChanceEntry(String outcomeId, BigDecimal weight, long microWeight,
                        BigDecimal displayPercentage, int yamlOrder) {
        this.outcomeId = Objects.requireNonNull(outcomeId);
        this.weight = Objects.requireNonNull(weight);
        this.microWeight = microWeight;
        this.displayPercentage = Objects.requireNonNull(displayPercentage);
        this.yamlOrder = yamlOrder;
    }

    public static ChanceEntry of(String outcomeId, BigDecimal weight, long microWeight,
                                  BigDecimal displayPercentage, int yamlOrder) {
        return new ChanceEntry(outcomeId, weight, microWeight, displayPercentage, yamlOrder);
    }

    public String getOutcomeId() { return outcomeId; }
    public BigDecimal getWeight() { return weight; }
    public long getMicroWeight() { return microWeight; }
    public BigDecimal getDisplayPercentage() { return displayPercentage; }
    public int getYamlOrder() { return yamlOrder; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChanceEntry)) return false;
        ChanceEntry that = (ChanceEntry) o;
        return microWeight == that.microWeight &&
               yamlOrder == that.yamlOrder &&
               Objects.equals(outcomeId, that.outcomeId) &&
               weight.compareTo(that.weight) == 0 &&
               displayPercentage.compareTo(that.displayPercentage) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcomeId, weight, microWeight, displayPercentage, yamlOrder);
    }

    @Override
    public String toString() {
        return "ChanceEntry{outcomeId=" + outcomeId + ", weight=" + weight +
               ", microWeight=" + microWeight + ", displayPercentage=" + displayPercentage +
               ", yamlOrder=" + yamlOrder + "}";
    }
}
