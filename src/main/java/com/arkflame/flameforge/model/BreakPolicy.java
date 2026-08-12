package com.arkflame.flameforge.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BreakPolicy {
    private static final String DEFAULT_RESULT_DISPLAY_NAME =
            "<gradient:#ef4444:#f97316><bold>Fractured %base_name%</bold></gradient>";
    private static final List<String> DEFAULT_RESULT_LORE = Collections.unmodifiableList(Arrays.asList(
            "<gray>The forge fractured this item.",
            "<dark_gray>Its enhancements were reset."
    ));

    private final boolean resetTier;
    private final int targetTier;
    private final boolean resetEnchants;
    private final boolean resetName;
    private final boolean resetLore;
    private final boolean resetAttributes;
    private final boolean resetPowers;
    private final boolean resetCustomModelData;
    private final boolean destroyItem;
    private final String resultDisplayName;
    private final List<String> resultLore;

    public BreakPolicy(boolean resetTier, int targetTier, boolean resetEnchants, boolean resetName,
                       boolean resetLore, boolean resetAttributes, boolean resetPowers,
                       boolean resetCustomModelData, boolean destroyItem) {
        this(resetTier, targetTier, resetEnchants, resetName, resetLore, resetAttributes,
                resetPowers, resetCustomModelData, destroyItem, DEFAULT_RESULT_DISPLAY_NAME,
                DEFAULT_RESULT_LORE);
    }

    public BreakPolicy(boolean resetTier, int targetTier, boolean resetEnchants, boolean resetName,
                       boolean resetLore, boolean resetAttributes, boolean resetPowers,
                       boolean resetCustomModelData, boolean destroyItem,
                       String resultDisplayName, List<String> resultLore) {
        this.resetTier = resetTier;
        this.targetTier = targetTier;
        this.resetEnchants = resetEnchants;
        this.resetName = resetName;
        this.resetLore = resetLore;
        this.resetAttributes = resetAttributes;
        this.resetPowers = resetPowers;
        this.resetCustomModelData = resetCustomModelData;
        this.destroyItem = destroyItem;
        this.resultDisplayName = resultDisplayName != null && !resultDisplayName.trim().isEmpty()
                ? resultDisplayName : DEFAULT_RESULT_DISPLAY_NAME;
        this.resultLore = resultLore != null && !resultLore.isEmpty()
                ? Collections.unmodifiableList(new java.util.ArrayList<>(resultLore))
                : DEFAULT_RESULT_LORE;
    }

    public static BreakPolicy none() {
        return new BreakPolicy(false, 0, false, false, false, false, false, false, false);
    }

    public static BreakPolicy defaultPolicy() {
        return new BreakPolicy(true, 0, true, true, true, true, true, true, false);
    }

    public boolean isResetTier() { return resetTier; }
    public int getTargetTier() { return targetTier; }
    public boolean isResetEnchants() { return resetEnchants; }
    public boolean isResetName() { return resetName; }
    public boolean isResetLore() { return resetLore; }
    public boolean isResetAttributes() { return resetAttributes; }
    public boolean isResetPowers() { return resetPowers; }
    public boolean isResetCustomModelData() { return resetCustomModelData; }
    public boolean isDestroyItem() { return destroyItem; }
    public String getResultDisplayName() { return resultDisplayName; }
    public List<String> getResultLore() { return resultLore; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BreakPolicy)) return false;
        BreakPolicy that = (BreakPolicy) o;
        return resetTier == that.resetTier &&
               targetTier == that.targetTier &&
               resetEnchants == that.resetEnchants &&
               resetName == that.resetName &&
               resetLore == that.resetLore &&
               resetAttributes == that.resetAttributes &&
               resetPowers == that.resetPowers &&
               resetCustomModelData == that.resetCustomModelData &&
               destroyItem == that.destroyItem &&
               Objects.equals(resultDisplayName, that.resultDisplayName) &&
               Objects.equals(resultLore, that.resultLore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resetTier, targetTier, resetEnchants, resetName, resetLore,
                           resetAttributes, resetPowers, resetCustomModelData, destroyItem,
                           resultDisplayName, resultLore);
    }

    @Override
    public String toString() {
        return "BreakPolicy{resetTier=" + resetTier + ", targetTier=" + targetTier +
               ", resetEnchants=" + resetEnchants + ", resetName=" + resetName +
               ", resetLore=" + resetLore + ", resetAttributes=" + resetAttributes +
               ", resetPowers=" + resetPowers + ", resetCustomModelData=" + resetCustomModelData +
               ", destroyItem=" + destroyItem + ", resultDisplayName=" + resultDisplayName +
               ", resultLore=" + resultLore + "}";
    }
}
