package com.arkflame.flameforge.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StationProfile {
    private final String id;
    private final String stationId;
    private final int maxTierUnlocked;
    private final List<String> requiredPermissions;

    private StationProfile(String id, String stationId, int maxTierUnlocked, List<String> requiredPermissions) {
        this.id = Objects.requireNonNull(id);
        this.stationId = Objects.requireNonNull(stationId);
        this.maxTierUnlocked = maxTierUnlocked;
        this.requiredPermissions = requiredPermissions != null ?
                                    Collections.unmodifiableList(requiredPermissions) :
                                    Collections.emptyList();
    }

    public static StationProfile of(String id, String stationId, int maxTierUnlocked, List<String> requiredPermissions) {
        return new StationProfile(id, stationId, maxTierUnlocked, requiredPermissions);
    }

    public String getId() { return id; }
    public String getStationId() { return stationId; }
    public int getMaxTierUnlocked() { return maxTierUnlocked; }
    public List<String> getRequiredPermissions() { return requiredPermissions; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StationProfile)) return false;
        StationProfile that = (StationProfile) o;
        return maxTierUnlocked == that.maxTierUnlocked &&
               Objects.equals(id, that.id) &&
               Objects.equals(stationId, that.stationId) &&
               Objects.equals(requiredPermissions, that.requiredPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, stationId, maxTierUnlocked, requiredPermissions);
    }

    @Override
    public String toString() {
        return "StationProfile{id=" + id + ", stationId=" + stationId +
               ", maxTierUnlocked=" + maxTierUnlocked + ", requiredPermissions=" + requiredPermissions + "}";
    }
}
