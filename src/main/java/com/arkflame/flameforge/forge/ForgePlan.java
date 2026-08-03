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

public final class ForgePlan {
    private final ItemStack input;
    private final int currentTierLevel;
    private final int targetTierLevel;
    private final TierRequirements requirements;
    private final TierChances chances;
    private final ForgeVariant selectedVariant;
    private final TierDefinition targetTier;
    private final CostQuote costQuote;

    private ForgePlan(Builder builder) {
        this.input = builder.input != null ? builder.input.clone() : null;
        this.currentTierLevel = builder.currentTierLevel;
        this.targetTierLevel = builder.targetTierLevel;
        this.requirements = builder.requirements;
        this.chances = builder.chances;
        this.selectedVariant = builder.selectedVariant;
        this.targetTier = builder.targetTier;
        this.costQuote = builder.costQuote;
    }

    public static ForgePlan create(Player player, PlayerForgeState session, ItemStack inputItem,
                                   TierDefinition targetTier, TierRequirements requirements,
                                   TierChances chances, ForgeVariant variant, CostQuote costQuote) {
        int currentLevel = session.getActiveTierLevel();
        return builder()
            .input(inputItem)
            .currentTierLevel(currentLevel)
            .targetTierLevel(currentLevel + 1)
            .requirements(requirements)
            .chances(chances)
            .selectedVariant(variant)
            .targetTier(targetTier)
            .costQuote(costQuote)
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

    public ForgeVariant getSelectedVariant() {
        return selectedVariant;
    }

    public TierDefinition getTargetTier() {
        return targetTier;
    }

    public CostQuote getCostQuote() {
        return costQuote;
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
        private ForgeVariant selectedVariant;
        private TierDefinition targetTier;
        private CostQuote costQuote;

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

        public Builder selectedVariant(ForgeVariant selectedVariant) {
            this.selectedVariant = selectedVariant;
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

        public ForgePlan build() {
            return new ForgePlan(this);
        }
    }
}
