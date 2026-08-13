package com.arkflame.flameforge.model;

import java.util.Objects;

public final class ForgeAttributeDefinition {
    public enum AttributeType {
        ATTACK_DAMAGE_FLAT,
        DAMAGE_REDUCTION_PERCENT,
        POISON_DAMAGE_REDUCTION_PERCENT,
        MAGIC_DAMAGE_REDUCTION_PERCENT,
        FALL_DAMAGE_REDUCTION_PERCENT;

        public static final AttributeType POISON_REDUCTION_PERCENT = POISON_DAMAGE_REDUCTION_PERCENT;
        public static final AttributeType MAGIC_REDUCTION_PERCENT = MAGIC_DAMAGE_REDUCTION_PERCENT;
        public static final AttributeType FALL_REDUCTION_PERCENT = FALL_DAMAGE_REDUCTION_PERCENT;
    }

    private final String id;
    private final AttributeType type;
    private final double multiplier;

    public ForgeAttributeDefinition(String id, AttributeType type, double multiplier) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.multiplier = multiplier;
    }

    public String getId() { return id; }
    public AttributeType getType() { return type; }
    public double getMultiplier() { return multiplier; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForgeAttributeDefinition)) return false;
        ForgeAttributeDefinition that = (ForgeAttributeDefinition) o;
        return Double.compare(that.multiplier, multiplier) == 0 &&
               Objects.equals(id, that.id) &&
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, multiplier);
    }

    @Override
    public String toString() {
        return "ForgeAttributeDefinition{id=" + id + ", type=" + type + ", multiplier=" + multiplier + "}";
    }
}
