package com.arkflame.flameforge.chance;

import com.arkflame.flameforge.model.OutcomeDefinition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class OutcomeSelector {
    private final RandomSource randomSource;

    public OutcomeSelector(RandomSource randomSource) {
        this.randomSource = Objects.requireNonNull(randomSource);
    }

    public ChanceTable buildChanceTable(List<OutcomeDefinition> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            throw new IllegalArgumentException("outcomes cannot be null or empty");
        }

        int n = outcomes.size();
        BigDecimal[] weights = new BigDecimal[n];
        String[] ids = new String[n];
        int[] orders = new int[n];

        for (int i = 0; i < n; i++) {
            OutcomeDefinition outcome = outcomes.get(i);
            weights[i] = outcome.getWeight();
            ids[i] = outcome.getId();
            orders[i] = outcome.getDisplayOrder();
        }

        return ChanceTable.from(weights, ids, orders);
    }

    public OutcomeDefinition select(List<OutcomeDefinition> outcomes, MaterialFilter materialFilter,
                                    PluginFilter pluginFilter, CapabilityFilter capabilityFilter,
                                    PlayerFilter playerFilter, CatalystFilter catalystFilter,
                                    WardFilter wardFilter) {
        if (outcomes == null || outcomes.isEmpty()) {
            throw new IllegalArgumentException("outcomes cannot be null or empty");
        }

        List<OutcomeDefinition> filtered = filterOutcomes(outcomes, materialFilter,
            pluginFilter, capabilityFilter, playerFilter, catalystFilter, wardFilter);

        if (filtered.isEmpty()) {
            throw new IllegalStateException("No outcomes passed filters");
        }

        ChanceTable table = buildChanceTable(filtered);
        long randomValue = randomSource.nextLong(table.getTotalMicroWeight());
        ChanceEntry selected = table.select(randomValue);

        for (OutcomeDefinition outcome : filtered) {
            if (outcome.getId().equals(selected.getOutcomeId())) {
                return outcome;
            }
        }
        throw new IllegalStateException("Selected outcome not found");
    }

    private List<OutcomeDefinition> filterOutcomes(List<OutcomeDefinition> outcomes,
                                                    MaterialFilter materialFilter,
                                                    PluginFilter pluginFilter,
                                                    CapabilityFilter capabilityFilter,
                                                    PlayerFilter playerFilter,
                                                    CatalystFilter catalystFilter,
                                                    WardFilter wardFilter) {
        return outcomes.stream()
            .filter(o -> materialFilter == null || materialFilter.test(o))
            .filter(o -> pluginFilter == null || pluginFilter.test(o))
            .filter(o -> capabilityFilter == null || capabilityFilter.test(o))
            .filter(o -> playerFilter == null || playerFilter.test(o))
            .filter(o -> catalystFilter == null || catalystFilter.test(o))
            .filter(o -> wardFilter == null || wardFilter.test(o))
            .collect(java.util.stream.Collectors.toList());
    }

    public interface MaterialFilter {
        boolean test(OutcomeDefinition outcome);
    }

    public interface PluginFilter {
        boolean test(OutcomeDefinition outcome);
    }

    public interface CapabilityFilter {
        boolean test(OutcomeDefinition outcome);
    }

    public interface PlayerFilter {
        boolean test(OutcomeDefinition outcome);
    }

    public interface CatalystFilter {
        boolean test(OutcomeDefinition outcome);
    }

    public interface WardFilter {
        boolean test(OutcomeDefinition outcome);
    }
}
