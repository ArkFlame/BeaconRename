package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.EnchantSpec;
import com.arkflame.flameforge.model.ForgeAnimationProfile;
import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.model.TierRequirements;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes a {@link TierDefinition} to a schema version 2 tier file.
 * Writes are atomic: a temporary file in the target directory is moved over the final file.
 */
public final class TierFileWriter {

    private TierFileWriter() {
    }

    public static boolean write(File tiersDirectory, TierDefinition tier) {
        if (tiersDirectory == null || tier == null || tier.getId() == null) {
            return false;
        }
        if (!tiersDirectory.exists() && !tiersDirectory.mkdirs()) {
            return false;
        }

        File target = new File(tiersDirectory, tier.getId() + ".yml");
        File temp = null;
        try {
            temp = File.createTempFile(".tier-", ".tmp", tiersDirectory);
            toYaml(tier).save(temp);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp.toPath());
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            }
            return false;
        }
    }

    public static YamlConfiguration toYaml(TierDefinition tier) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", tier.getId());
        yaml.set("level", tier.getLevel());
        yaml.set("enabled", tier.isEnabled());
        yaml.set("permission", tier.getPermission());

        writeDisplay(yaml, tier.getDisplay());
        writeInput(yaml, tier);
        writeRequirements(yaml, tier.getRequirements());
        writeChances(yaml, tier.getChances());
        writeBreakPolicy(yaml, tier.getBreakPolicy());
        writeCurse(yaml, tier.getCurseDefinition());
        writeAnimation(yaml, tier.getAnimationProfile());
        writeVariants(yaml, tier.getVariants());
        return yaml;
    }

    private static void writeDisplay(YamlConfiguration yaml, TierDefinition.TierDisplay display) {
        if (display == null) {
            return;
        }
        yaml.set("display.name", display.getName());
        yaml.set("display.lore", new ArrayList<>(display.getLore()));
        yaml.set("display.glow", display.isGlow());
        yaml.set("display.icon", display.getIcon());
    }

    private static void writeInput(YamlConfiguration yaml, TierDefinition tier) {
        yaml.set("input.allowed-groups", new ArrayList<>(tier.getAllowedGroups()));
        yaml.set("input.denied-materials", new ArrayList<>(tier.getDeniedMaterials()));
        yaml.set("input.cooldown-seconds", tier.getCooldownSeconds());
    }

    private static void writeRequirements(YamlConfiguration yaml, TierRequirements requirements) {
        if (requirements == null) {
            return;
        }
        yaml.set("requirements.combine", requirements.getCombine().name());

        TierRequirements.XpRequirement xp = requirements.getXp();
        if (xp != null) {
            yaml.set("requirements.xp.enabled", xp.isEnabled());
            yaml.set("requirements.xp.amount", xp.getLevel());
        }

        TierRequirements.MoneyRequirement money = requirements.getMoney();
        if (money != null) {
            yaml.set("requirements.money.enabled", money.isEnabled());
            yaml.set("requirements.money.amount", money.getAmount().toPlainString());
        }

        TierRequirements.ItemsRequirement items = requirements.getItems();
        if (items != null) {
            yaml.set("requirements.items.enabled", items.isEnabled());
            List<Map<String, Object>> required = new ArrayList<>();
            for (TierRequirements.ItemRequirement item : items.getItems()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("materials", new ArrayList<>(item.getMaterialCandidates()));
                entry.put("amount", item.getAmount());
                entry.put("display-name", item.getDisplayName());
                required.add(entry);
            }
            yaml.set("requirements.items.required", required);
        }
    }

    private static void writeChances(YamlConfiguration yaml, TierChances chances) {
        if (chances == null) {
            return;
        }
        yaml.set("chances.success", chances.getSuccessPercent().toPlainString());
        yaml.set("chances.break", chances.getBreakPercent().toPlainString());
        yaml.set("chances.curse", chances.getCursePercent().toPlainString());
        for (Map.Entry<String, java.math.BigDecimal> entry : chances.getVariantPercents().entrySet()) {
            yaml.set("chances." + entry.getKey(), entry.getValue().toPlainString());
        }
    }

    private static void writeBreakPolicy(YamlConfiguration yaml, BreakPolicy policy) {
        if (policy == null) {
            return;
        }
        yaml.set("break.reset-tier", policy.isResetTier());
        yaml.set("break.target-tier", policy.getTargetTier());
        yaml.set("break.destroy-item", policy.isDestroyItem());
        yaml.set("break.result-display-name", policy.getResultDisplayName());
        yaml.set("break.result-lore", new ArrayList<>(policy.getResultLore()));
        yaml.set("break.reset-display-name", policy.isResetName());
        yaml.set("break.reset-lore", policy.isResetLore());
        yaml.set("break.reset-enchantments", policy.isResetEnchants());
        yaml.set("break.reset-attributes", policy.isResetAttributes());
        yaml.set("break.reset-powers", policy.isResetPowers());
        yaml.set("break.reset-custom-model-data", policy.isResetCustomModelData());
    }

    private static void writeCurse(YamlConfiguration yaml, CurseDefinition curse) {
        if (curse == null) {
            return;
        }
        yaml.set("curse.display-name", curse.getName());
        yaml.set("curse.lore", new ArrayList<>(curse.getLore()));
        yaml.set("curse.enchantment-candidates", new ArrayList<>(curse.getEnchantments()));
    }

    private static void writeAnimation(YamlConfiguration yaml, ForgeAnimationProfile animation) {
        if (animation == null) {
            return;
        }
        yaml.set("animation.duration-ticks", animation.getDurationTicks());
        yaml.set("animation.interval-ticks", animation.getIntervalTicks());

        ForgeAnimationProfile.ChargeSound chargeSound = animation.getChargeSound();
        if (chargeSound != null) {
            yaml.set("animation.charge-sound.candidates", new ArrayList<>(chargeSound.getCandidates()));
            yaml.set("animation.charge-sound.volume", chargeSound.getVolume().toPlainString());
            yaml.set("animation.charge-sound.start-pitch", chargeSound.getStartPitch().toPlainString());
            yaml.set("animation.charge-sound.end-pitch", chargeSound.getEndPitch().toPlainString());
        }

        ForgeAnimationProfile.ChargeParticle chargeParticle = animation.getChargeParticle();
        if (chargeParticle != null) {
            yaml.set("animation.charge-particle.candidates", new ArrayList<>(chargeParticle.getCandidates()));
            yaml.set("animation.charge-particle.count", chargeParticle.getCount());
            yaml.set("animation.charge-particle.radius", chargeParticle.getRadius().toPlainString());
        }

        ForgeAnimationProfile.ImpactParticle impactParticle = animation.getImpactParticle();
        if (impactParticle != null) {
            yaml.set("animation.impact-particle.candidates", new ArrayList<>(impactParticle.getCandidates()));
            yaml.set("animation.impact-particle.count", impactParticle.getCount());
            yaml.set("animation.impact-particle.radius", impactParticle.getRadius().toPlainString());
        }

        writeFeedback(yaml, "animation.success", animation.getSuccessFeedback());
        writeFeedback(yaml, "animation.break", animation.getBreakFeedback());
        writeFeedback(yaml, "animation.curse", animation.getCurseFeedback());
    }

    private static void writeFeedback(YamlConfiguration yaml, String path, ForgeAnimationProfile.OutcomeFeedback feedback) {
        if (feedback == null) {
            return;
        }
        yaml.set(path + ".sound-candidates", new ArrayList<>(feedback.getSoundCandidates()));
        yaml.set(path + ".particle-candidates", new ArrayList<>(feedback.getParticleCandidates()));
        yaml.set(path + ".title", feedback.getTitle());
        yaml.set(path + ".action-bar", feedback.getActionBar());
        yaml.set(path + ".subtitle", new ArrayList<>(feedback.getSubtitle()));
    }

    private static void writeVariants(YamlConfiguration yaml, List<ForgeVariant> variants) {
        if (variants == null) {
            return;
        }
        for (ForgeVariant variant : variants) {
            String path = "variants." + variant.getId();
            yaml.set(path + ".weight", String.valueOf(variant.getWeight()));
            yaml.set(path + ".applicable-groups", new ArrayList<>(variant.getApplicableGroups()));
            yaml.set(path + ".display-name", variant.getName());
            yaml.set(path + ".lore", new ArrayList<>(variant.getLore()));
            if (variant.getIcon() != null) {
                yaml.set(path + ".icon", variant.getIcon());
            }
            yaml.set(path + ".enchantments", enchantments(variant.getEnchantments()));
            yaml.set(path + ".attributes", attributes(variant.getAttributes()));
            yaml.set(path + ".powers", powers(variant.getPowers()));
        }
    }

    private static List<Map<String, Object>> enchantments(List<EnchantSpec> specs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (EnchantSpec spec : specs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("candidates", new ArrayList<>(java.util.Collections.singletonList(spec.getEnchantmentId())));
            entry.put("min-level", spec.getMinLevel());
            entry.put("max-level", spec.getMaxLevel());
            entry.put("unsafe", spec.isUnsafe());
            result.add(entry);
        }
        return result;
    }

    private static List<Map<String, Object>> attributes(List<ForgeAttributeDefinition> specs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ForgeAttributeDefinition spec : specs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", spec.getId());
            entry.put("type", spec.getType().name());
            entry.put("value", spec.getMultiplier());
            result.add(entry);
        }
        return result;
    }

    private static List<Map<String, Object>> powers(List<ForgePowerDefinition> specs) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ForgePowerDefinition power : specs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", power.getId());
            entry.put("type", power.getType().name());
            entry.put("cooldown-ticks", power.getCooldownTicks());
            entry.put("hit-interval", power.getHitInterval());
            entry.put("chance", power.getChance().toPlainString());
            entry.put("effect-candidates", new ArrayList<>(power.getEffectCandidates()));
            entry.put("duration-ticks", power.getDurationTicks());
            entry.put("amplifier", power.getAmplifier());
            entry.put("fire-ticks", power.getFireTicks());
            entry.put("heal-amount", power.getHealAmount().toPlainString());
            entry.put("horizontal-strength", power.getHorizontalStrength().toPlainString());
            entry.put("vertical-strength", power.getVerticalStrength().toPlainString());
            List<String> slots = new ArrayList<>();
            for (ForgePowerDefinition.ActivationSlot slot : power.getActivationSlots()) {
                slots.add(slot.name());
            }
            entry.put("activation-slots", slots);
            entry.put("particle-candidates", new ArrayList<>(power.getParticleCandidates()));
            entry.put("radius", power.getRadius().toPlainString());
            entry.put("damage-amount", power.getDamageAmount().toPlainString());
            entry.put("pulse-count", power.getPulseCount());
            entry.put("pulse-interval-ticks", power.getPulseIntervalTicks());
            entry.put("max-targets", power.getMaxTargets());
            entry.put("chain-delay-ticks", power.getChainDelayTicks());
            entry.put("trail-points", power.getTrailPoints());
            entry.put("primary-knockback-multiplier", power.getPrimaryKnockbackMultiplier().toPlainString());
            entry.put("secondary-damage-multiplier", power.getSecondaryDamageMultiplier().toPlainString());
            result.add(entry);
        }
        return result;
    }
}
