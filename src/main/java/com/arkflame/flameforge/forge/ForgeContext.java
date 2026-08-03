package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.StationProfile;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class ForgeContext {
    private final UUID transactionId;
    private final String playerId;
    private final ForgePlan plan;
    private final PlayerForgeState playerState;
    private final ConfigSnapshot configSnapshot;
    private final StationProfile stationProfile;
    private final Location stationLocation;
    private final long createdAt;
    private ForgeTransaction currentTransaction;
    private boolean transactionCompleted;

    private ForgeContext(Builder builder) {
        this.transactionId = Objects.requireNonNull(builder.transactionId);
        this.playerId = Objects.requireNonNull(builder.playerId);
        this.plan = Objects.requireNonNull(builder.plan);
        this.playerState = Objects.requireNonNull(builder.playerState);
        this.configSnapshot = Objects.requireNonNull(builder.configSnapshot);
        this.stationProfile = builder.stationProfile;
        this.stationLocation = builder.stationLocation;
        this.createdAt = System.currentTimeMillis();
        this.currentTransaction = null;
        this.transactionCompleted = false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public ForgePlan getPlan() {
        return plan;
    }

    public PlayerForgeState getPlayerState() {
        return playerState;
    }

    public ConfigSnapshot getConfigSnapshot() {
        return configSnapshot;
    }

    public StationProfile getStationProfile() {
        return stationProfile;
    }

    public Location getStationLocation() {
        return stationLocation;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public ForgeTransaction getCurrentTransaction() {
        return currentTransaction;
    }

    public void setCurrentTransaction(ForgeTransaction transaction) {
        this.currentTransaction = transaction;
    }

    public boolean isTransactionCompleted() {
        return transactionCompleted;
    }

    public boolean tryMarkCompleted() {
        if (transactionCompleted) {
            return false;
        }
        transactionCompleted = true;
        return true;
    }

    public ItemStack getInputItem() {
        return plan != null ? plan.getInput() : null;
    }

    public int getCurrentTierLevel() {
        return plan != null ? plan.getCurrentTierLevel() : 0;
    }

    public int getTargetTierLevel() {
        return plan != null ? plan.getTargetTierLevel() : 0;
    }

    public static final class Builder {
        private UUID transactionId;
        private String playerId;
        private ForgePlan plan;
        private PlayerForgeState playerState;
        private ConfigSnapshot configSnapshot;
        private StationProfile stationProfile;
        private Location stationLocation;

        public Builder transactionId(UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder playerId(String playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder plan(ForgePlan plan) {
            this.plan = plan;
            return this;
        }

        public Builder playerState(PlayerForgeState playerState) {
            this.playerState = playerState;
            return this;
        }

        public Builder configSnapshot(ConfigSnapshot configSnapshot) {
            this.configSnapshot = configSnapshot;
            return this;
        }

        public Builder stationProfile(StationProfile stationProfile) {
            this.stationProfile = stationProfile;
            return this;
        }

        public Builder stationLocation(Location stationLocation) {
            this.stationLocation = stationLocation;
            return this;
        }

        public ForgeContext build() {
            return new ForgeContext(this);
        }
    }
}
