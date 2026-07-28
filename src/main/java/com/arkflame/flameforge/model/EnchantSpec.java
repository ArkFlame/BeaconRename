package com.arkflame.flameforge.model;

import java.util.Objects;

public final class EnchantSpec {
    private final String enchantment;
    private final int minLevel;
    private final int maxLevel;

    private EnchantSpec(String enchantment, int minLevel, int maxLevel) {
        this.enchantment = enchantment;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
    }

    public static EnchantSpec of(String enchantment, int minLevel, int maxLevel) {
        return new EnchantSpec(Objects.requireNonNull(enchantment), minLevel, maxLevel);
    }

    public static EnchantSpec of(String enchantment) {
        return new EnchantSpec(enchantment, 1, Integer.MAX_VALUE);
    }

    public String getEnchantment() { return enchantment; }
    public int getMinLevel() { return minLevel; }
    public int getMaxLevel() { return maxLevel; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnchantSpec)) return false;
        EnchantSpec that = (EnchantSpec) o;
        return minLevel == that.minLevel &&
               maxLevel == that.maxLevel &&
               Objects.equals(enchantment, that.enchantment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enchantment, minLevel, maxLevel);
    }

    @Override
    public String toString() {
        return "EnchantSpec{enchantment=" + enchantment + ", minLevel=" + minLevel + ", maxLevel=" + maxLevel + "}";
    }
}
