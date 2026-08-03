package com.arkflame.flameforge.chance;

import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierChances;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class OutcomeSelector {
    private final RandomSource randomSource;

    public OutcomeSelector(RandomSource randomSource) {
        this.randomSource = Objects.requireNonNull(randomSource);
    }

    public ForgeOutcomeCategory rollCategory(TierChances chances) {
        if (chances == null) {
            throw new IllegalArgumentException("chances cannot be null");
        }
        double total = chances.getSuccessPercent() + chances.getBreakPercent() + chances.getCursePercent();
        if (total <= 0) {
            return ForgeOutcomeCategory.BREAK;
        }
        double roll = randomSource.nextDouble() * total;
        double successThreshold = chances.getSuccessPercent();
        double breakThreshold = successThreshold + chances.getBreakPercent();
        if (roll < successThreshold) {
            return ForgeOutcomeCategory.SUCCESS;
        } else if (roll < breakThreshold) {
            return ForgeOutcomeCategory.BREAK;
        } else {
            return ForgeOutcomeCategory.CURSE;
        }
    }

    public ForgeVariant selectVariant(List<ForgeVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        int n = variants.size();
        double totalWeight = 0;
        double[] cumulativeWeights = new double[n];
        for (int i = 0; i < n; i++) {
            double w = variants.get(i).getWeight();
            totalWeight += w;
            cumulativeWeights[i] = totalWeight;
        }
        if (totalWeight <= 0) {
            return variants.get(0);
        }
        double roll = randomSource.nextDouble() * totalWeight;
        for (int i = 0; i < n; i++) {
            if (roll < cumulativeWeights[i]) {
                return variants.get(i);
            }
        }
        return variants.get(n - 1);
    }

    public ChanceTable buildChanceTable(List<ChanceEntry> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            throw new IllegalArgumentException("outcomes cannot be null or empty");
        }

        int n = outcomes.size();
        BigDecimal[] weights = new BigDecimal[n];
        String[] ids = new String[n];
        int[] orders = new int[n];

        for (int i = 0; i < n; i++) {
            ChanceEntry outcome = outcomes.get(i);
            weights[i] = outcome.getWeight();
            ids[i] = outcome.getOutcomeId();
            orders[i] = outcome.getYamlOrder();
        }

        return ChanceTable.from(weights, ids, orders);
    }
}
