package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.model.OutcomeDefinition;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.persistence.StationRepository;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ForgeContext {
    private final UUID transactionId;
    private final String playerId;
    private final ItemStack[] inputSlots;
    private final ItemStack[] catalystSlots;
    private final ItemStack[] wardSlots;
    private final int tierLevel;
    private final String stationId;
    private final StationProfile stationProfile;
    private final PlayerForgeState playerState;
    private final ConfigSnapshot configSnapshot;
    private final Location stationLocation;
    private final long createdAt;

    private ForgeContext(Builder builder) {
        this.transactionId = Objects.requireNonNull(builder.transactionId);
        this.playerId = Objects.requireNonNull(builder.playerId);
        this.inputSlots = cloneItems(builder.inputSlots);
        this.catalystSlots = cloneItems(builder.catalystSlots);
        this.wardSlots = cloneItems(builder.wardSlots);
        this.tierLevel = builder.tierLevel;
        this.stationId = builder.stationId;
        this.stationProfile = builder.stationProfile;
        this.playerState = Objects.requireNonNull(builder.playerState);
        this.configSnapshot = Objects.requireNonNull(builder.configSnapshot);
        this.stationLocation = builder.stationLocation;
        this.createdAt = System.currentTimeMillis();
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getTransactionId() { return transactionId; }
    public String getPlayerId() { return playerId; }
    public ItemStack[] getInputSlots() { return cloneItems(inputSlots); }
    public ItemStack[] getCatalystSlots() { return cloneItems(catalystSlots); }
    public ItemStack[] getWardSlots() { return cloneItems(wardSlots); }
    public int getTierLevel() { return tierLevel; }
    public String getStationId() { return stationId; }
    public StationProfile getStationProfile() { return stationProfile; }
    public PlayerForgeState getPlayerState() { return playerState; }
    public ConfigSnapshot getConfigSnapshot() { return configSnapshot; }
    public Location getStationLocation() { return stationLocation; }
    public long getCreatedAt() { return createdAt; }

    public boolean hasInputItems() {
        if (inputSlots == null) return false;
        for (ItemStack item : inputSlots) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCatalystItems() {
        if (catalystSlots == null) return false;
        for (ItemStack item : catalystSlots) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                return true;
            }
        }
        return false;
    }

    public boolean hasWardItems() {
        if (wardSlots == null) return false;
        for (ItemStack item : wardSlots) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack[] cloneItems(ItemStack[] original) {
        if (original == null) return null;
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                copy[i] = original[i].clone();
            }
        }
        return copy;
    }

    public static final class Builder {
        private UUID transactionId;
        private String playerId;
        private ItemStack[] inputSlots;
        private ItemStack[] catalystSlots;
        private ItemStack[] wardSlots;
        private int tierLevel;
        private String stationId;
        private StationProfile stationProfile;
        private PlayerForgeState playerState;
        private ConfigSnapshot configSnapshot;
        private Location stationLocation;

        public Builder transactionId(UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder playerId(String playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder inputSlots(ItemStack[] inputSlots) {
            this.inputSlots = inputSlots;
            return this;
        }

        public Builder catalystSlots(ItemStack[] catalystSlots) {
            this.catalystSlots = catalystSlots;
            return this;
        }

        public Builder wardSlots(ItemStack[] wardSlots) {
            this.wardSlots = wardSlots;
            return this;
        }

        public Builder tierLevel(int tierLevel) {
            this.tierLevel = tierLevel;
            return this;
        }

        public Builder stationId(String stationId) {
            this.stationId = stationId;
            return this;
        }

        public Builder stationProfile(StationProfile stationProfile) {
            this.stationProfile = stationProfile;
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

        public Builder stationLocation(Location stationLocation) {
            this.stationLocation = stationLocation;
            return this;
        }

        public ForgeContext build() {
            return new ForgeContext(this);
        }
    }
}
