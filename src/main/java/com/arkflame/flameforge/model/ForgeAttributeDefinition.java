package com.arkflame.flameforge.model;

import java.util.Objects;

public final class ForgeAttributeDefinition {
    public enum AttributeType {
        ATTACK_DAMAGE_FLAT,
        DAMAGE_REDUCTION_PERCENT
    }

    private final AttributeType type;
    private final double multiplier;

    public ForgeAttributeDefinition(AttributeType type, double multiplier) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.multiplier = multiplier;
    }

    public AttributeType getType() { return type; }
    public double getMultiplier() { return multiplier; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForgeAttributeDefinition)) return false;
        ForgeAttributeDefinition that = (ForgeAttributeDefinition) o;
        return Double.compare(that.multiplier, multiplier) == 0 && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, multiplier);
    }

    @Override
    public String toString() {
        return "ForgeAttributeDefinition{type=" + type + ", multiplier=" + multiplier + "}";
    }
}
