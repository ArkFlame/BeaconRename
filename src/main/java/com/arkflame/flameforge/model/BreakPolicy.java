package com.arkflame.flameforge.model;

import java.util.Objects;

public final class BreakPolicy {
    private final boolean resetTier;
    private final boolean resetEnchants;
    private final boolean resetName;
    private final boolean resetLore;
    private final boolean resetAttributes;
    private final boolean resetPowers;
    private final boolean resetIdentity;
    private final boolean destroyItem;

    public BreakPolicy(boolean resetTier, boolean resetEnchants, boolean resetName,
                       boolean resetLore, boolean resetAttributes, boolean resetPowers,
                       boolean resetIdentity, boolean destroyItem) {
        this.resetTier = resetTier;
        this.resetEnchants = resetEnchants;
        this.resetName = resetName;
        this.resetLore = resetLore;
        this.resetAttributes = resetAttributes;
        this.resetPowers = resetPowers;
        this.resetIdentity = resetIdentity;
        this.destroyItem = destroyItem;
    }

    public static BreakPolicy none() {
        return new BreakPolicy(false, false, false, false, false, false, false, false);
    }

    public static BreakPolicy defaultPolicy() {
        return none();
    }

    public boolean isResetTier() { return resetTier; }
    public boolean isResetEnchants() { return resetEnchants; }
    public boolean isResetName() { return resetName; }
    public boolean isResetLore() { return resetLore; }
    public boolean isResetAttributes() { return resetAttributes; }
    public boolean isResetPowers() { return resetPowers; }
    public boolean isResetIdentity() { return resetIdentity; }
    public boolean isDestroyItem() { return destroyItem; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BreakPolicy)) return false;
        BreakPolicy that = (BreakPolicy) o;
        return resetTier == that.resetTier &&
               resetEnchants == that.resetEnchants &&
               resetName == that.resetName &&
               resetLore == that.resetLore &&
               resetAttributes == that.resetAttributes &&
               resetPowers == that.resetPowers &&
               resetIdentity == that.resetIdentity &&
               destroyItem == that.destroyItem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resetTier, resetEnchants, resetName, resetLore,
                           resetAttributes, resetPowers, resetIdentity, destroyItem);
    }

    @Override
    public String toString() {
        return "BreakPolicy{resetTier=" + resetTier + ", resetEnchants=" + resetEnchants +
               ", resetName=" + resetName + ", resetLore=" + resetLore +
               ", resetAttributes=" + resetAttributes + ", resetPowers=" + resetPowers +
               ", resetIdentity=" + resetIdentity + ", destroyItem=" + destroyItem + "}";
    }
}
