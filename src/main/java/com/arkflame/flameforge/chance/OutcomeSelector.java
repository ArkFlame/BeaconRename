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
        double total = chances.getSuccessPercent().doubleValue() + chances.getBreakPercent().doubleValue() + chances.getCursePercent().doubleValue();
        if (total <= 0) {
            return ForgeOutcomeCategory.BREAK;
        }
        double roll = randomSource.nextDouble() * total;
        double successThreshold = chances.getSuccessPercent().doubleValue();
        double breakThreshold = successThreshold + chances.getBreakPercent().doubleValue();
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
        ChanceTable table = buildVariantChanceTable(variants);
        long randomMicro = randomSource.nextLong(table.getTotalMicroWeight());
        int index = table.selectIndex(randomMicro);
        return variants.get(index);
    }

    public ChanceTable buildVariantChanceTable(List<ForgeVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("variants cannot be null or empty");
        }

        int n = variants.size();
        BigDecimal[] weights = new BigDecimal[n];
        String[] ids = new String[n];
        int[] yamlOrders = new int[n];

        for (int i = 0; i < n; i++) {
            ForgeVariant variant = variants.get(i);
            weights[i] = BigDecimal.valueOf(variant.getWeight());
            ids[i] = variant.getId();
            yamlOrders[i] = i;
        }

        return ChanceTable.from(weights, ids, yamlOrders);
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
