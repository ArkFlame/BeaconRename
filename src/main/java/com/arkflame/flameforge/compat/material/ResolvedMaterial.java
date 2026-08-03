package com.arkflame.flameforge.compat.material;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ResolvedMaterial {
    private final Material material;
    private final short legacyData;
    private final boolean applyLegacyData;

    ResolvedMaterial(Material material, short legacyData, boolean applyLegacyData) {
        if (material == null) {
            throw new IllegalArgumentException("material cannot be null");
        }
        this.material = material;
        this.legacyData = legacyData;
        this.applyLegacyData = applyLegacyData;
    }

    public Material getMaterial() {
        return material;
    }

    public short getLegacyData() {
        return legacyData;
    }

    public boolean shouldApplyLegacyData() {
        return applyLegacyData;
    }

    public Material toBukkitMaterial() {
        return material;
    }

    public ItemStack toItemStack(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (applyLegacyData) {
            return new ItemStack(material, amount, legacyData);
        }
        return new ItemStack(material, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResolvedMaterial that = (ResolvedMaterial) o;
        return legacyData == that.legacyData &&
                applyLegacyData == that.applyLegacyData &&
                material == that.material;
    }

    @Override
    public int hashCode() {
        int result = material.hashCode();
        result = 31 * result + (int) legacyData;
        result = 31 * result + (applyLegacyData ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ResolvedMaterial{material=" + material + ", legacyData=" + legacyData + ", applyLegacyData=" + applyLegacyData + "}";
    }
}