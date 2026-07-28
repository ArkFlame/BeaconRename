package com.arkflame.flameforge.chance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ChanceTable {
    private static final BigDecimal SCALE = new BigDecimal("1E-6");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int DISPLAY_SCALE = 1;

    private final List<ChanceEntry> entries;
    private final long totalMicroWeight;
    private final BigDecimal displayTotal;

    private ChanceTable(List<ChanceEntry> entries, long totalMicroWeight, BigDecimal displayTotal) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries)));
        this.totalMicroWeight = totalMicroWeight;
        this.displayTotal = displayTotal;
    }

    public static ChanceTable from(BigDecimal[] weights, String[] outcomeIds, int[] yamlOrders) {
        if (weights == null || outcomeIds == null || weights.length != outcomeIds.length) {
            throw new IllegalArgumentException("weights and outcomeIds must be non-null and equal length");
        }
        if (weights.length == 0) {
            throw new IllegalArgumentException("At least one weight is required");
        }

        int n = weights.length;
        long[] microWeights = new long[n];
        long sum = 0;

        for (int i = 0; i < n; i++) {
            BigDecimal w = weights[i];
            if (w == null || w.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("weight must be positive");
            }
            if (w.scale() > 6) {
                throw new IllegalArgumentException("weight scale cannot exceed 6");
            }
            microWeights[i] = w.setScale(6, RoundingMode.UNNECESSARY).unscaledValue().longValue();
            sum = Math.addExact(sum, microWeights[i]);
        }

        BigDecimal sumBd = new BigDecimal(sum);
        BigDecimal displaySum = BigDecimal.ZERO;
        int maxIdx = 0;
        long maxMicro = microWeights[0];

        List<ChanceEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal percentage = microWeights[i] == 0 ? BigDecimal.ZERO :
                new BigDecimal(microWeights[i]).multiply(HUNDRED).divide(sumBd, 6, RoundingMode.HALF_UP);
            BigDecimal displayPct = percentage.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);

            if (microWeights[i] > maxMicro) {
                maxMicro = microWeights[i];
                maxIdx = i;
            }

            displaySum = displaySum.add(displayPct);
            entries.add(ChanceEntry.of(outcomeIds[i], weights[i], microWeights[i],
                                       displayPct, yamlOrders != null ? yamlOrders[i] : i));
        }

        BigDecimal residual = HUNDRED.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).subtract(displaySum);
        if (residual.compareTo(BigDecimal.ZERO) != 0) {
            ChanceEntry maxEntry = entries.get(maxIdx);
            entries.set(maxIdx, ChanceEntry.of(
                maxEntry.getOutcomeId(),
                maxEntry.getWeight(),
                maxEntry.getMicroWeight(),
                maxEntry.getDisplayPercentage().add(residual),
                maxEntry.getYamlOrder()
            ));
        }

        return new ChanceTable(entries, sum, HUNDRED.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP));
    }

    public List<ChanceEntry> getEntries() { return entries; }
    public long getTotalMicroWeight() { return totalMicroWeight; }
    public BigDecimal getDisplayTotal() { return displayTotal; }

    public int selectIndex(long randomMicro) {
        if (randomMicro < 0 || randomMicro >= totalMicroWeight) {
            throw new IllegalArgumentException("randomMicro out of range [0, " + totalMicroWeight + ")");
        }
        long cumulative = 0;
        for (int i = 0; i < entries.size(); i++) {
            cumulative += entries.get(i).getMicroWeight();
            if (randomMicro < cumulative) {
                return i;
            }
        }
        return entries.size() - 1;
    }

    public ChanceEntry select(long randomMicro) {
        return entries.get(selectIndex(randomMicro));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChanceTable)) return false;
        ChanceTable that = (ChanceTable) o;
        return totalMicroWeight == that.totalMicroWeight &&
               Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries, totalMicroWeight);
    }

    @Override
    public String toString() {
        return "ChanceTable{entries=" + entries + ", totalMicroWeight=" + totalMicroWeight +
               ", displayTotal=" + displayTotal + "}";
    }
}
