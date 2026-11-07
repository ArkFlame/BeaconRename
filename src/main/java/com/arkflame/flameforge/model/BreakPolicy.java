package com.arkflame.flameforge.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BreakPolicy {
    /** English fallback text for break results when no catalog text is supplied. */
    public static final class DefaultText {
        public static final DefaultText ENGLISH = new DefaultText(
                "<gradient:#ef4444:#f97316><bold>Fractured %base_name%</bold></gradient>",
                Arrays.asList(
                        "<gray>The forge fractured this item.",
                        "<dark_gray>Its enhancements were reset."));

        private final String displayName;
        private final List<String> lore;

        private DefaultText(String displayName, List<String> lore) {
            this.displayName = displayName;
            this.lore = Collections.unmodifiableList(new java.util.ArrayList<>(lore));
        }

        /** Blank or missing values fall back to the English defaults. */
        public static DefaultText of(String displayName, List<String> lore) {
            String name = displayName != null && !displayName.trim().isEmpty()
                    ? displayName : ENGLISH.displayName;
            List<String> resolvedLore = lore != null && !lore.isEmpty() ? lore : ENGLISH.lore;
            return new DefaultText(name, resolvedLore);
        }

        public String getDisplayName() { return displayName; }
        public List<String> getLore() { return lore; }
    }

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
                resetPowers, resetCustomModelData, destroyItem, DefaultText.ENGLISH.getDisplayName(),
                DefaultText.ENGLISH.getLore());
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
                ? resultDisplayName : DefaultText.ENGLISH.getDisplayName();
        this.resultLore = resultLore != null && !resultLore.isEmpty()
                ? Collections.unmodifiableList(new java.util.ArrayList<>(resultLore))
                : DefaultText.ENGLISH.getLore();
    }

    public static BreakPolicy none() {
        return new BreakPolicy(false, 0, false, false, false, false, false, false, false);
    }

    public static BreakPolicy defaultPolicy() {
        return defaultPolicy(DefaultText.ENGLISH);
    }

    public static BreakPolicy defaultPolicy(DefaultText text) {
        return new BreakPolicy(true, 0, true, true, true, true, true, true, false,
                text.getDisplayName(), text.getLore());
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
