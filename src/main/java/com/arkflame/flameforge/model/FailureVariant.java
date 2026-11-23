package com.arkflame.flameforge.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A configurable consequence applied when a forge reforge fails.
 *
 * <p>Failure variants live under {@code break-system.failure-variants} in config.yml.
 * The built-in ids are {@code break} and {@code curse}; servers may add any number of
 * custom ids and give each one a matching {@code chances.<id>} entry in a tier file.
 */
public final class FailureVariant {
    private final String id;
    private final boolean enabled;
    private final boolean destroyItem;
    private final String displayName;
    private final List<String> lore;
    private final boolean durabilityHalf;
    private final double damageReduction;
    private final List<String> enchantments;
    private final boolean resetTier;
    private final int targetTier;
    private final boolean resetEnchantments;
    private final boolean resetDisplayName;
    private final boolean resetLore;
    private final boolean resetAttributes;
    private final boolean resetPowers;
    private final boolean resetCustomModelData;
    private final boolean markAsFailed;
    private final String message;
    private final List<String> particleCandidates;
    private final List<String> soundCandidates;
    private final String title;
    private final List<String> subtitle;

    public FailureVariant(String id, boolean enabled, boolean destroyItem, String displayName,
                          List<String> lore, boolean durabilityHalf, double damageReduction,
                          List<String> enchantments, boolean resetTier, int targetTier,
                          boolean resetEnchantments, boolean resetDisplayName, boolean resetLore,
                          boolean resetAttributes, boolean resetPowers, boolean resetCustomModelData,
                          boolean markAsFailed, String message, List<String> particleCandidates,
                          List<String> soundCandidates, String title, List<String> subtitle) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.enabled = enabled;
        this.destroyItem = destroyItem;
        this.displayName = displayName != null ? displayName : "";
        this.lore = copy(lore);
        this.durabilityHalf = durabilityHalf;
        this.damageReduction = damageReduction;
        this.enchantments = copy(enchantments);
        this.resetTier = resetTier;
        this.targetTier = targetTier;
        this.resetEnchantments = resetEnchantments;
        this.resetDisplayName = resetDisplayName;
        this.resetLore = resetLore;
        this.resetAttributes = resetAttributes;
        this.resetPowers = resetPowers;
        this.resetCustomModelData = resetCustomModelData;
        this.markAsFailed = markAsFailed;
        this.message = message != null ? message : "";
        this.particleCandidates = copy(particleCandidates);
        this.soundCandidates = copy(soundCandidates);
        this.title = title != null ? title : "";
        this.subtitle = copy(subtitle);
    }

    private static List<String> copy(List<String> values) {
        return values != null ? Collections.unmodifiableList(new ArrayList<>(values)) : Collections.emptyList();
    }

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public boolean isDestroyItem() { return destroyItem; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public boolean isDurabilityHalf() { return durabilityHalf; }
    public double getDamageReduction() { return damageReduction; }
    public List<String> getEnchantments() { return enchantments; }
    public boolean isResetTier() { return resetTier; }
    public int getTargetTier() { return targetTier; }
    public boolean isResetEnchantments() { return resetEnchantments; }
    public boolean isResetDisplayName() { return resetDisplayName; }
    public boolean isResetLore() { return resetLore; }
    public boolean isResetAttributes() { return resetAttributes; }
    public boolean isResetPowers() { return resetPowers; }
    public boolean isResetCustomModelData() { return resetCustomModelData; }
    public boolean isMarkAsFailed() { return markAsFailed; }
    public String getMessage() { return message; }
    public List<String> getParticleCandidates() { return particleCandidates; }
    public List<String> getSoundCandidates() { return soundCandidates; }
    public String getTitle() { return title; }
    public List<String> getSubtitle() { return subtitle; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FailureVariant)) return false;
        FailureVariant that = (FailureVariant) o;
        return enabled == that.enabled && destroyItem == that.destroyItem &&
               durabilityHalf == that.durabilityHalf &&
               Double.compare(that.damageReduction, damageReduction) == 0 &&
               resetTier == that.resetTier && targetTier == that.targetTier &&
               resetEnchantments == that.resetEnchantments &&
               resetDisplayName == that.resetDisplayName && resetLore == that.resetLore &&
               resetAttributes == that.resetAttributes && resetPowers == that.resetPowers &&
               resetCustomModelData == that.resetCustomModelData && markAsFailed == that.markAsFailed &&
               Objects.equals(id, that.id) && Objects.equals(displayName, that.displayName) &&
               Objects.equals(lore, that.lore) && Objects.equals(enchantments, that.enchantments) &&
               Objects.equals(message, that.message) &&
               Objects.equals(particleCandidates, that.particleCandidates) &&
               Objects.equals(soundCandidates, that.soundCandidates) &&
               Objects.equals(title, that.title) && Objects.equals(subtitle, that.subtitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, enabled, destroyItem, displayName, lore, durabilityHalf,
            damageReduction, enchantments, resetTier, targetTier, resetEnchantments,
            resetDisplayName, resetLore, resetAttributes, resetPowers, resetCustomModelData,
            markAsFailed, message, particleCandidates, soundCandidates, title, subtitle);
    }

    @Override
    public String toString() {
        return "FailureVariant{id=" + id + ", enabled=" + enabled +
               ", destroyItem=" + destroyItem + ", durabilityHalf=" + durabilityHalf +
               ", damageReduction=" + damageReduction + ", markAsFailed=" + markAsFailed +
               ", message=" + message + "}";
    }
}
