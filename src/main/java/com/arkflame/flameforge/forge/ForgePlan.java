package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.model.TierRequirements;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class ForgePlan {
    private final ItemStack input;
    private final int currentTierLevel;
    private final int targetTierLevel;
    private final TierRequirements requirements;
    private final TierChances chances;
    private final TierDefinition targetTier;
    private final CostQuote costQuote;
    private final String stationId;
    private final String stationProfileId;
    private final UUID stationWorldUuid;
    private final String stationWorldName;
    private final int stationBlockX;
    private final int stationBlockY;
    private final int stationBlockZ;

    private ForgePlan(Builder builder) {
        this.input = builder.input != null ? builder.input.clone() : null;
        this.currentTierLevel = builder.currentTierLevel;
        this.targetTierLevel = builder.targetTierLevel;
        this.requirements = builder.requirements;
        this.chances = builder.chances;
        this.targetTier = builder.targetTier;
        this.costQuote = builder.costQuote;
        this.stationId = builder.stationId;
        this.stationProfileId = builder.stationProfileId;
        this.stationWorldUuid = builder.stationWorldUuid;
        this.stationWorldName = builder.stationWorldName;
        this.stationBlockX = builder.stationBlockX;
        this.stationBlockY = builder.stationBlockY;
        this.stationBlockZ = builder.stationBlockZ;
    }

    public static ForgePlan create(Player player, PlayerForgeState session, ItemStack inputItem,
                                   TierDefinition targetTier, TierRequirements requirements,
                                   TierChances chances, CostQuote costQuote,
                                   String stationId, String stationProfileId,
                                   UUID stationWorldUuid, String stationWorldName,
                                   int stationBlockX, int stationBlockY, int stationBlockZ) {
        return builder()
            .input(inputItem)
            .currentTierLevel(session.getActiveTierLevel())
            .targetTierLevel(session.getActiveTierLevel() + 1)
            .requirements(requirements)
            .chances(chances)
            .targetTier(targetTier)
            .costQuote(costQuote)
            .stationId(stationId)
            .stationProfileId(stationProfileId)
            .stationWorldUuid(stationWorldUuid)
            .stationWorldName(stationWorldName)
            .stationBlockX(stationBlockX)
            .stationBlockY(stationBlockY)
            .stationBlockZ(stationBlockZ)
            .build();
    }

    public static ForgePlan createWithTier(ItemStack inputItem, int currentTierLevel,
                                   TierDefinition targetTier, TierRequirements requirements,
                                   TierChances chances, CostQuote costQuote,
                                   String stationId, String stationProfileId,
                                   UUID stationWorldUuid, String stationWorldName,
                                   int stationBlockX, int stationBlockY, int stationBlockZ) {
        return builder()
            .input(inputItem)
            .currentTierLevel(currentTierLevel)
            .targetTierLevel(currentTierLevel + 1)
            .requirements(requirements)
            .chances(chances)
            .targetTier(targetTier)
            .costQuote(costQuote)
            .stationId(stationId)
            .stationProfileId(stationProfileId)
            .stationWorldUuid(stationWorldUuid)
            .stationWorldName(stationWorldName)
            .stationBlockX(stationBlockX)
            .stationBlockY(stationBlockY)
            .stationBlockZ(stationBlockZ)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ItemStack getInput() {
        return input != null ? input.clone() : null;
    }

    public int getCurrentTierLevel() {
        return currentTierLevel;
    }

    public int getTargetTierLevel() {
        return targetTierLevel;
    }

    public TierRequirements getRequirements() {
        return requirements;
    }

    public TierChances getChances() {
        return chances;
    }

    public TierDefinition getTargetTier() {
        return targetTier;
    }

    public CostQuote getCostQuote() {
        return costQuote;
    }

    public String getStationId() {
        return stationId;
    }

    public String getStationProfileId() {
        return stationProfileId;
    }

    public UUID getStationWorldUuid() {
        return stationWorldUuid;
    }

    public String getStationWorldName() {
        return stationWorldName;
    }

    public int getStationBlockX() {
        return stationBlockX;
    }

    public int getStationBlockY() {
        return stationBlockY;
    }

    public int getStationBlockZ() {
        return stationBlockZ;
    }

    public boolean isAffordable() {
        return costQuote != null && costQuote.isAffordable();
    }

    public static final class Builder {
        private ItemStack input;
        private int currentTierLevel;
        private int targetTierLevel;
        private TierRequirements requirements;
        private TierChances chances;
        private TierDefinition targetTier;
        private CostQuote costQuote;
        private String stationId;
        private String stationProfileId;
        private UUID stationWorldUuid;
        private String stationWorldName;
        private int stationBlockX;
        private int stationBlockY;
        private int stationBlockZ;

        public Builder input(ItemStack input) {
            this.input = input;
            return this;
        }

        public Builder currentTierLevel(int currentTierLevel) {
            this.currentTierLevel = currentTierLevel;
            return this;
        }

        public Builder targetTierLevel(int targetTierLevel) {
            this.targetTierLevel = targetTierLevel;
            return this;
        }

        public Builder requirements(TierRequirements requirements) {
            this.requirements = requirements;
            return this;
        }

        public Builder chances(TierChances chances) {
            this.chances = chances;
            return this;
        }

        public Builder targetTier(TierDefinition targetTier) {
            this.targetTier = targetTier;
            return this;
        }

        public Builder costQuote(CostQuote costQuote) {
            this.costQuote = costQuote;
            return this;
        }

        public Builder stationId(String stationId) {
            this.stationId = stationId;
            return this;
        }

        public Builder stationProfileId(String stationProfileId) {
            this.stationProfileId = stationProfileId;
            return this;
        }

        public Builder stationWorldUuid(UUID stationWorldUuid) {
            this.stationWorldUuid = stationWorldUuid;
            return this;
        }

        public Builder stationWorldName(String stationWorldName) {
            this.stationWorldName = stationWorldName;
            return this;
        }

        public Builder stationBlockX(int stationBlockX) {
            this.stationBlockX = stationBlockX;
            return this;
        }

        public Builder stationBlockY(int stationBlockY) {
            this.stationBlockY = stationBlockY;
            return this;
        }

        public Builder stationBlockZ(int stationBlockZ) {
            this.stationBlockZ = stationBlockZ;
            return this;
        }

        public ForgePlan build() {
            return new ForgePlan(this);
        }
    }
}
