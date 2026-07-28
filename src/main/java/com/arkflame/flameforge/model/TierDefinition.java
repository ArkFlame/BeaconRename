package com.arkflame.flameforge.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TierDefinition {
    private final String id;
    private final int tierLevel;
    private final TierCost cost;
    private final int successAnimationDuration;
    private final int failAnimationDuration;
    private final List<OutcomeDefinition> outcomes;

    private TierDefinition(String id, int tierLevel, TierCost cost,
                           int successAnimationDuration, int failAnimationDuration,
                           List<OutcomeDefinition> outcomes) {
        this.id = Objects.requireNonNull(id);
        this.tierLevel = tierLevel;
        this.cost = Objects.requireNonNull(cost);
        this.successAnimationDuration = successAnimationDuration;
        this.failAnimationDuration = failAnimationDuration;
        this.outcomes = outcomes != null ? Collections.unmodifiableList(outcomes) : Collections.emptyList();
    }

    public static TierDefinition of(String id, int tierLevel, TierCost cost,
                                     int successAnimationDuration, int failAnimationDuration,
                                     List<OutcomeDefinition> outcomes) {
        return new TierDefinition(id, tierLevel, cost, successAnimationDuration, failAnimationDuration, outcomes);
    }

    public String getId() { return id; }
    public int getTierLevel() { return tierLevel; }
    public TierCost getCost() { return cost; }
    public int getSuccessAnimationDuration() { return successAnimationDuration; }
    public int getFailAnimationDuration() { return failAnimationDuration; }
    public List<OutcomeDefinition> getOutcomes() { return outcomes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TierDefinition)) return false;
        TierDefinition that = (TierDefinition) o;
        return tierLevel == that.tierLevel &&
               successAnimationDuration == that.successAnimationDuration &&
               failAnimationDuration == that.failAnimationDuration &&
               Objects.equals(id, that.id) &&
               Objects.equals(cost, that.cost) &&
               Objects.equals(outcomes, that.outcomes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tierLevel, cost, successAnimationDuration, failAnimationDuration, outcomes);
    }

    @Override
    public String toString() {
        return "TierDefinition{id=" + id + ", tierLevel=" + tierLevel + ", cost=" + cost +
               ", successAnimationDuration=" + successAnimationDuration +
               ", failAnimationDuration=" + failAnimationDuration + ", outcomes=" + outcomes + "}";
    }
}
