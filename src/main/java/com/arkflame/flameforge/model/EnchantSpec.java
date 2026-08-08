package com.arkflame.flameforge.model;

import java.util.Objects;

public final class EnchantSpec {
    private final String enchantmentId;
    private final int level;
    private final int minLevel;
    private final int maxLevel;
    private final boolean unsafe;

    public EnchantSpec(String enchantmentId, int level) {
        this(enchantmentId, level, 0, Integer.MAX_VALUE, false);
    }

    public EnchantSpec(String enchantmentId, int level, int minLevel, int maxLevel) {
        this(enchantmentId, level, minLevel, maxLevel, false);
    }

    public EnchantSpec(String enchantmentId, int level, int minLevel, int maxLevel, boolean unsafe) {
        this.enchantmentId = Objects.requireNonNull(enchantmentId, "enchantmentId cannot be null");
        this.level = level;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.unsafe = unsafe;
    }

    public String getEnchantmentId() { return enchantmentId; }
    public int getLevel() { return level; }
    public int getMinLevel() { return minLevel; }
    public int getMaxLevel() { return maxLevel; }
    public boolean isUnsafe() { return unsafe; }
    public String getEnchantment() { return enchantmentId; }

    public static EnchantSpec of(String enchantmentId, int level, int minLevel, int maxLevel) {
        return new EnchantSpec(enchantmentId, level, minLevel, maxLevel, false);
    }

    public static EnchantSpec of(String enchantmentId, int level, int minLevel, int maxLevel, boolean unsafe) {
        return new EnchantSpec(enchantmentId, level, minLevel, maxLevel, unsafe);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnchantSpec)) return false;
        EnchantSpec that = (EnchantSpec) o;
        return unsafe == that.unsafe
            && level == that.level
            && minLevel == that.minLevel
            && maxLevel == that.maxLevel
            && Objects.equals(enchantmentId, that.enchantmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enchantmentId, level, minLevel, maxLevel, unsafe);
    }

    @Override
    public String toString() {
        return "EnchantSpec{enchantmentId=" + enchantmentId + ", level=" + level
            + ", minLevel=" + minLevel + ", maxLevel=" + maxLevel + ", unsafe=" + unsafe + "}";
    }
}
