package com.arkflame.flameforge.model;

import java.util.Objects;

public final class EnchantSpec {
    private final String enchantmentId;
    private final int level;
    private final int minLevel;
    private final int maxLevel;

    public EnchantSpec(String enchantmentId, int level) {
        this(enchantmentId, level, 0, Integer.MAX_VALUE);
    }

    public EnchantSpec(String enchantmentId, int level, int minLevel, int maxLevel) {
        this.enchantmentId = Objects.requireNonNull(enchantmentId, "enchantmentId cannot be null");
        this.level = level;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
    }

    public String getEnchantmentId() { return enchantmentId; }
    public int getLevel() { return level; }
    public int getMinLevel() { return minLevel; }
    public int getMaxLevel() { return maxLevel; }
    public String getEnchantment() { return enchantmentId; }

    public static EnchantSpec of(String enchantmentId, int level, int minLevel) {
        return new EnchantSpec(enchantmentId, level);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnchantSpec)) return false;
        EnchantSpec that = (EnchantSpec) o;
        return level == that.level && Objects.equals(enchantmentId, that.enchantmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enchantmentId, level);
    }

    @Override
    public String toString() {
        return "EnchantSpec{enchantmentId=" + enchantmentId + ", level=" + level + "}";
    }
}
