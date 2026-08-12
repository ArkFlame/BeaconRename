package com.arkflame.flameforge.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.ArrayList;
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
        EVERY_N_HIT_KNOCKBACK,
        ON_HIT_AOE_FIRE,
        ON_HIT_BLEED,
        ON_HIT_EXPLOSIVE,
        ON_HIT_CHAIN_POTION,
        ON_HIT_CHAIN_DAMAGE
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
    private final List<String> particleCandidates;
    private final BigDecimal radius;
    private final BigDecimal damageAmount;
    private final int pulseCount;
    private final int pulseIntervalTicks;
    private final int maxTargets;
    private final int chainDelayTicks;
    private final int trailPoints;
    private final BigDecimal primaryKnockbackMultiplier;
    private final BigDecimal secondaryDamageMultiplier;

    public ForgePowerDefinition(String id, PowerType powerType, int cooldownTicks, int hitInterval,
                                BigDecimal chance, List<String> effectCandidates,
                                 int durationTicks, int amplifier, int fireTicks,
                                 BigDecimal healAmount, BigDecimal horizontalStrength,
                                 BigDecimal verticalStrength, List<ActivationSlot> activationSlots) {
        this(id, powerType, cooldownTicks, hitInterval, chance, effectCandidates, durationTicks,
            amplifier, fireTicks, healAmount, horizontalStrength, verticalStrength, activationSlots,
            Collections.<String>emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, 1, 10, 1, 0, 8,
            BigDecimal.ONE, BigDecimal.ZERO);
    }

    public ForgePowerDefinition(String id, PowerType powerType, int cooldownTicks, int hitInterval,
                                 BigDecimal chance, List<String> effectCandidates,
                                 int durationTicks, int amplifier, int fireTicks,
                                 BigDecimal healAmount, BigDecimal horizontalStrength,
                                 BigDecimal verticalStrength, List<ActivationSlot> activationSlots,
                                 List<String> particleCandidates, BigDecimal radius,
                                 BigDecimal damageAmount, int pulseCount, int pulseIntervalTicks,
                                 int maxTargets, int chainDelayTicks, int trailPoints,
                                 BigDecimal primaryKnockbackMultiplier,
                                 BigDecimal secondaryDamageMultiplier) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.powerType = Objects.requireNonNull(powerType, "powerType cannot be null");
        this.cooldownTicks = cooldownTicks;
        this.hitInterval = hitInterval;
        this.chance = requireRange(chance != null ? chance : BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, "chance");
        this.effectCandidates = immutableList(effectCandidates);
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.fireTicks = fireTicks;
        this.healAmount = healAmount != null ? healAmount : BigDecimal.ZERO;
        this.horizontalStrength = horizontalStrength != null ? horizontalStrength : BigDecimal.ONE;
        this.verticalStrength = verticalStrength != null ? verticalStrength : BigDecimal.ZERO;
        this.activationSlots = immutableList(activationSlots);
        this.particleCandidates = immutableList(particleCandidates);
        this.radius = requireRange(radius != null ? radius : BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("16"), "radius");
        this.damageAmount = requireRange(damageAmount != null ? damageAmount : BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("40"), "damageAmount");
        this.pulseCount = requireRange(pulseCount, 1, 20, "pulseCount");
        this.pulseIntervalTicks = requireRange(pulseIntervalTicks, 1, 200, "pulseIntervalTicks");
        this.maxTargets = requireRange(maxTargets, 1, 16, "maxTargets");
        this.chainDelayTicks = requireRange(chainDelayTicks, 0, 40, "chainDelayTicks");
        this.trailPoints = requireRange(trailPoints, 2, 32, "trailPoints");
        this.primaryKnockbackMultiplier = requireRange(primaryKnockbackMultiplier != null ? primaryKnockbackMultiplier : BigDecimal.ONE,
            BigDecimal.ONE, new BigDecimal("4"), "primaryKnockbackMultiplier");
        this.secondaryDamageMultiplier = requireRange(secondaryDamageMultiplier != null ? secondaryDamageMultiplier : BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ONE, "secondaryDamageMultiplier");
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null || values.isEmpty() ? Collections.<T>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static BigDecimal requireRange(BigDecimal value, BigDecimal min, BigDecimal max, String name) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(name + " out of range: " + value);
        }
        return value;
    }

    private static int requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range: " + value);
        }
        return value;
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
    public List<String> getParticleCandidates() { return particleCandidates; }
    public BigDecimal getRadius() { return radius; }
    public BigDecimal getDamageAmount() { return damageAmount; }
    public int getPulseCount() { return pulseCount; }
    public int getPulseIntervalTicks() { return pulseIntervalTicks; }
    public int getMaxTargets() { return maxTargets; }
    public int getChainDelayTicks() { return chainDelayTicks; }
    public int getTrailPoints() { return trailPoints; }
    public BigDecimal getPrimaryKnockbackMultiplier() { return primaryKnockbackMultiplier; }
    public BigDecimal getSecondaryDamageMultiplier() { return secondaryDamageMultiplier; }

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
