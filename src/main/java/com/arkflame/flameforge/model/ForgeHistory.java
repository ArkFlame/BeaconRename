package com.arkflame.flameforge.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ForgeHistory {
    private final String playerId;
    private final Instant timestamp;
    private final String stationId;
    private final int tierLevel;
    private final String outcomeId;
    private final OutcomeType outcomeType;
    private final List<String> logs;

    private ForgeHistory(String playerId, Instant timestamp, String stationId,
                         int tierLevel, String outcomeId, OutcomeType outcomeType, List<String> logs) {
        this.playerId = Objects.requireNonNull(playerId);
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.stationId = stationId;
        this.tierLevel = tierLevel;
        this.outcomeId = outcomeId;
        this.outcomeType = outcomeType;
        this.logs = logs != null ? Collections.unmodifiableList(logs) : Collections.emptyList();
    }

    public static ForgeHistory of(String playerId, Instant timestamp, String stationId,
                                  int tierLevel, String outcomeId, OutcomeType outcomeType, List<String> logs) {
        return new ForgeHistory(playerId, timestamp, stationId, tierLevel, outcomeId, outcomeType, logs);
    }

    public String getPlayerId() { return playerId; }
    public Instant getTimestamp() { return timestamp; }
    public String getStationId() { return stationId; }
    public int getTierLevel() { return tierLevel; }
    public String getOutcomeId() { return outcomeId; }
    public OutcomeType getOutcomeType() { return outcomeType; }
    public List<String> getLogs() { return logs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForgeHistory)) return false;
        ForgeHistory that = (ForgeHistory) o;
        return tierLevel == that.tierLevel &&
               Objects.equals(playerId, that.playerId) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(stationId, that.stationId) &&
               Objects.equals(outcomeId, that.outcomeId) &&
               outcomeType == that.outcomeType &&
               Objects.equals(logs, that.logs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, timestamp, stationId, tierLevel, outcomeId, outcomeType, logs);
    }

    @Override
    public String toString() {
        return "ForgeHistory{playerId=" + playerId + ", timestamp=" + timestamp +
               ", stationId=" + stationId + ", tierLevel=" + tierLevel +
               ", outcomeId=" + outcomeId + ", outcomeType=" + outcomeType + ", logs=" + logs + "}";
    }
}
