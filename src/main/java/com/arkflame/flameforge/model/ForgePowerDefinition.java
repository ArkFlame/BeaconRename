package com.arkflame.flameforge.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ForgePowerDefinition {
    public enum PowerType {
        ON_HIT_POTION,
        ON_HIT_FIRE,
        ON_HIT_HEAL,
        PASSIVE_POTION,
        SHIFT_RIGHT_CLICK_DASH,
        SHIFT_RIGHT_CLICK_HEAL,
        EVERY_N_HIT_LIGHTNING,
        EVERY_N_HIT_KNOCKBACK
    }

    public enum ActivationSlot {
        MAINHAND,
        OFFHAND,
        HEAD,
        CHEST,
        LEGS,
        FEET
    }

    private final String id;
    private final PowerType powerType;
    private final int cooldownTicks;
    private final int hitInterval;
    private final BigDecimal chance;
    private final List<String> effectCandidates;
    private final int durationTicks;
    private final int amplifier;
    private final int fireTicks;
    private final BigDecimal healAmount;
    private final BigDecimal horizontalStrength;
    private final BigDecimal verticalStrength;
    private final List<ActivationSlot> activationSlots;

    public ForgePowerDefinition(String id, PowerType powerType, int cooldownTicks, int hitInterval,
                                BigDecimal chance, List<String> effectCandidates,
                                int durationTicks, int amplifier, int fireTicks,
                                BigDecimal healAmount, BigDecimal horizontalStrength,
                                BigDecimal verticalStrength, List<ActivationSlot> activationSlots) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.powerType = Objects.requireNonNull(powerType, "powerType cannot be null");
        this.cooldownTicks = cooldownTicks;
        this.hitInterval = hitInterval;
        this.chance = chance != null ? chance : BigDecimal.ONE;
        this.effectCandidates = effectCandidates != null ? Collections.unmodifiableList(effectCandidates) : Collections.emptyList();
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.fireTicks = fireTicks;
        this.healAmount = healAmount != null ? healAmount : BigDecimal.ZERO;
        this.horizontalStrength = horizontalStrength != null ? horizontalStrength : BigDecimal.ONE;
        this.verticalStrength = verticalStrength != null ? verticalStrength : BigDecimal.ZERO;
        this.activationSlots = activationSlots != null ? Collections.unmodifiableList(activationSlots) : Collections.emptyList();
    }

    public String getId() { return id; }
    public PowerType getType() { return powerType; }
    public PowerType getPowerType() { return powerType; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getHitInterval() { return hitInterval; }
    public BigDecimal getChance() { return chance; }
    public List<String> getEffectCandidates() { return effectCandidates; }
    public int getDurationTicks() { return durationTicks; }
    public int getAmplifier() { return amplifier; }
    public int getFireTicks() { return fireTicks; }
    public BigDecimal getHealAmount() { return healAmount; }
    public BigDecimal getHorizontalStrength() { return horizontalStrength; }
    public BigDecimal getVerticalStrength() { return verticalStrength; }
    public List<ActivationSlot> getActivationSlots() { return activationSlots; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForgePowerDefinition)) return false;
        ForgePowerDefinition that = (ForgePowerDefinition) o;
        return Objects.equals(id, that.id) && powerType == that.powerType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, powerType);
    }

    @Override
    public String toString() {
        return "ForgePowerDefinition{id=" + id + ", powerType=" + powerType + "}";
    }
}
