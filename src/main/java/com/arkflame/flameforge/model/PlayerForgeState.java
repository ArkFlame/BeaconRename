package com.arkflame.flameforge.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class PlayerForgeState {
    private final String playerId;
    private final ForgeSessionState sessionState;
    private final String activeStationId;
    private final int activeTierLevel;
    private final Map<String, Integer> pityCounter;
    private final Map<String, Long> cooldownExpiry;

    private PlayerForgeState(String playerId, ForgeSessionState sessionState, String activeStationId,
                             int activeTierLevel, Map<String, Integer> pityCounter,
                             Map<String, Long> cooldownExpiry) {
        this.playerId = Objects.requireNonNull(playerId);
        this.sessionState = sessionState != null ? sessionState : ForgeSessionState.OPEN;
        this.activeStationId = activeStationId;
        this.activeTierLevel = activeTierLevel;
        this.pityCounter = pityCounter != null ?
                          Collections.unmodifiableMap(new HashMap<>(pityCounter)) :
                          Collections.emptyMap();
        this.cooldownExpiry = cooldownExpiry != null ?
                             Collections.unmodifiableMap(new HashMap<>(cooldownExpiry)) :
                             Collections.emptyMap();
    }

    public static PlayerForgeState of(String playerId) {
        return new PlayerForgeState(playerId, ForgeSessionState.OPEN, null, 0,
                                    Collections.emptyMap(), Collections.emptyMap());
    }

    public static PlayerForgeState of(String playerId, ForgeSessionState sessionState,
                                       String activeStationId, int activeTierLevel,
                                       Map<String, Integer> pityCounter, Map<String, Long> cooldownExpiry) {
        return new PlayerForgeState(playerId, sessionState, activeStationId, activeTierLevel,
                                    pityCounter, cooldownExpiry);
    }

    public PlayerForgeState withSessionState(ForgeSessionState newState) {
        return new PlayerForgeState(playerId, newState, activeStationId, activeTierLevel,
                                    pityCounter, cooldownExpiry);
    }

    public PlayerForgeState withActiveStation(String stationId, int tierLevel) {
        return new PlayerForgeState(playerId, ForgeSessionState.OPEN, stationId, tierLevel,
                                    pityCounter, cooldownExpiry);
    }

    public PlayerForgeState withIncrementedPity(String stationId) {
        Map<String, Integer> newPity = new HashMap<>(pityCounter);
        newPity.merge(stationId, 1, Integer::sum);
        return new PlayerForgeState(playerId, sessionState, activeStationId, activeTierLevel,
                                    newPity, cooldownExpiry);
    }

    public PlayerForgeState withPityReset(String stationId) {
        Map<String, Integer> newPity = new HashMap<>(pityCounter);
        newPity.remove(stationId);
        return new PlayerForgeState(playerId, sessionState, activeStationId, activeTierLevel,
                                    newPity, cooldownExpiry);
    }

    public PlayerForgeState withCooldownSet(String stationId, long durationMillis) {
        Map<String, Long> newCooldown = new HashMap<>(cooldownExpiry);
        newCooldown.put(stationId, Instant.now().toEpochMilli() + durationMillis);
        return new PlayerForgeState(playerId, sessionState, activeStationId, activeTierLevel,
                                    pityCounter, newCooldown);
    }

    public PlayerForgeState withExpiredCooldownsCleared() {
        long now = Instant.now().toEpochMilli();
        Map<String, Long> newCooldown = new HashMap<>();
        for (Map.Entry<String, Long> entry : cooldownExpiry.entrySet()) {
            if (entry.getValue() > now) {
                newCooldown.put(entry.getKey(), entry.getValue());
            }
        }
        return new PlayerForgeState(playerId, sessionState, activeStationId, activeTierLevel,
                                    pityCounter, newCooldown);
    }

    public String getPlayerId() { return playerId; }
    public ForgeSessionState getSessionState() { return sessionState; }
    public String getActiveStationId() { return activeStationId; }
    public int getActiveTierLevel() { return activeTierLevel; }
    public Map<String, Integer> getPityCounter() { return pityCounter; }
    public Map<String, Long> getCooldownExpiry() { return cooldownExpiry; }

    public int getPityCount(String stationId) {
        return pityCounter.getOrDefault(stationId, 0);
    }

    public boolean isOnCooldown(String stationId) {
        Long expiry = cooldownExpiry.get(stationId);
        return expiry != null && Instant.now().toEpochMilli() < expiry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerForgeState)) return false;
        PlayerForgeState that = (PlayerForgeState) o;
        return activeTierLevel == that.activeTierLevel &&
               sessionState == that.sessionState &&
               Objects.equals(playerId, that.playerId) &&
               Objects.equals(activeStationId, that.activeStationId) &&
               Objects.equals(pityCounter, that.pityCounter) &&
               Objects.equals(cooldownExpiry, that.cooldownExpiry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, sessionState, activeStationId, activeTierLevel,
                           pityCounter, cooldownExpiry);
    }

    @Override
    public String toString() {
        return "PlayerForgeState{playerId=" + playerId + ", sessionState=" + sessionState +
               ", activeStationId=" + activeStationId + ", activeTierLevel=" + activeTierLevel +
               ", pityCounter=" + pityCounter + ", cooldownExpiry=" + cooldownExpiry + "}";
    }
}
