package com.arkflame.flameforge.model;

import java.util.Objects;

public final class ItemMatcherSpec {
    private final String material;
    private final String name;
    private final int minDurability;
    private final int maxDurability;
    private final boolean exactMatch;

    private ItemMatcherSpec(String material, String name, int minDurability, int maxDurability, boolean exactMatch) {
        this.material = material;
        this.name = name;
        this.minDurability = minDurability;
        this.maxDurability = maxDurability;
        this.exactMatch = exactMatch;
    }

    public static ItemMatcherSpec of(String material, String name, int minDurability, int maxDurability, boolean exactMatch) {
        return new ItemMatcherSpec(Objects.requireNonNull(material), name, minDurability, maxDurability, exactMatch);
    }

    public static ItemMatcherSpec of(String material) {
        return new ItemMatcherSpec(material, null, -1, -1, false);
    }

    public String getMaterial() { return material; }
    public String getName() { return name; }
    public int getMinDurability() { return minDurability; }
    public int getMaxDurability() { return maxDurability; }
    public boolean isExactMatch() { return exactMatch; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemMatcherSpec)) return false;
        ItemMatcherSpec that = (ItemMatcherSpec) o;
        return minDurability == that.minDurability &&
               maxDurability == that.maxDurability &&
               exactMatch == that.exactMatch &&
               Objects.equals(material, that.material) &&
               Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, name, minDurability, maxDurability, exactMatch);
    }

    @Override
    public String toString() {
        return "ItemMatcherSpec{material=" + material + ", name=" + name + ", minDurability=" + minDurability +
               ", maxDurability=" + maxDurability + ", exactMatch=" + exactMatch + "}";
    }
}
