package com.arkflame.flameforge.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StationDefinition {
    private final String id;
    private final String displayName;
    private final List<TierDefinition> tiers;

    private StationDefinition(String id, String displayName, List<TierDefinition> tiers) {
        this.id = Objects.requireNonNull(id);
        this.displayName = displayName;
        this.tiers = tiers != null ? Collections.unmodifiableList(tiers) : Collections.emptyList();
    }

    public static StationDefinition of(String id, String displayName, List<TierDefinition> tiers) {
        return new StationDefinition(id, displayName, tiers);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public List<TierDefinition> getTiers() { return tiers; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StationDefinition)) return false;
        StationDefinition that = (StationDefinition) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(displayName, that.displayName) &&
               Objects.equals(tiers, that.tiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, tiers);
    }

    @Override
    public String toString() {
        return "StationDefinition{id=" + id + ", displayName=" + displayName + ", tiers=" + tiers + "}";
    }
}
