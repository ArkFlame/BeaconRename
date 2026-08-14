package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;

import java.util.Locale;
import java.util.Objects;

public final class ForgeAnimationThemeResolver {
    private static final ForgeAnimationTheme BREAK = new ForgeAnimationTheme(
        "break", 239, 68, 68, 255, 130, 130, "smoke", "crit");
    private static final ForgeAnimationTheme CURSE = new ForgeAnimationTheme(
        "curse", 168, 85, 247, 214, 180, 254, "portal", "spell");
    private static final ForgeAnimationTheme ELECTRIC = new ForgeAnimationTheme(
        "electric", 250, 204, 21, 254, 240, 138, "flame", "firework");
    private static final ForgeAnimationTheme EXPLOSIVE = new ForgeAnimationTheme(
        "explosive", 249, 115, 22, 254, 185, 120, "flame", "firework");
    private static final ForgeAnimationTheme CONTAGION = new ForgeAnimationTheme(
        "contagion", 132, 204, 22, 199, 230, 132, "flame", "firework");
    private static final ForgeAnimationTheme POISON = new ForgeAnimationTheme(
        "poison", 34, 197, 94, 134, 227, 165, "flame", "firework");
    private static final ForgeAnimationTheme BLEED = new ForgeAnimationTheme(
        "bleed", 220, 38, 38, 250, 150, 150, "flame", "firework");
    private static final ForgeAnimationTheme SWIFT = new ForgeAnimationTheme(
        "swift", 56, 189, 248, 180, 226, 254, "flame", "firework");
    private static final ForgeAnimationTheme HEAL = new ForgeAnimationTheme(
        "heal", 244, 114, 182, 250, 186, 216, "flame", "firework");
    private static final ForgeAnimationTheme DEFENSIVE = new ForgeAnimationTheme(
        "defensive", 96, 165, 250, 178, 212, 254, "flame", "firework");
    private static final ForgeAnimationTheme SUCCESS = new ForgeAnimationTheme(
        "success", 245, 158, 11, 254, 214, 130, "flame", "firework");

    public ForgeAnimationTheme resolve(ForgeOutcomeCategory category, ForgeVariant usedVariant) {
        Objects.requireNonNull(category, "category");
        if (category == ForgeOutcomeCategory.SUCCESS && usedVariant == null) {
            throw new IllegalArgumentException("usedVariant must not be null for SUCCESS");
        }
        switch (category) {
            case BREAK:
                return BREAK;
            case CURSE:
                return CURSE;
            case SUCCESS:
            default:
                return resolveSuccess(usedVariant);
        }
    }

    private static ForgeAnimationTheme resolveSuccess(ForgeVariant variant) {
        if (hasPowerType(variant, ForgePowerDefinition.PowerType.ON_HIT_CHAIN_DAMAGE)
            || hasPowerType(variant, ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING)) {
            return ELECTRIC;
        }
        if (hasPowerType(variant, ForgePowerDefinition.PowerType.ON_HIT_EXPLOSIVE)) {
            return EXPLOSIVE;
        }
        if (hasPowerType(variant, ForgePowerDefinition.PowerType.ON_HIT_CHAIN_POTION)) {
            return CONTAGION;
        }
        if (hasEffectCandidate(variant, ForgePowerDefinition.PowerType.ON_HIT_POTION, "POISON")) {
            return POISON;
        }
        if (hasPowerType(variant, ForgePowerDefinition.PowerType.ON_HIT_BLEED)) {
            return BLEED;
        }
        if (hasPowerType(variant, ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH)
            || hasEffectCandidate(variant, ForgePowerDefinition.PowerType.PASSIVE_POTION, "SPEED")) {
            return SWIFT;
        }
        if (hasPowerType(variant, ForgePowerDefinition.PowerType.ON_HIT_HEAL)
            || hasPowerType(variant, ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_HEAL)
            || hasPowerType(variant, ForgePowerDefinition.PowerType.ON_BLOCK_HEAL)
            || hasEffectCandidate(variant, ForgePowerDefinition.PowerType.PASSIVE_POTION, "REGENERATION")) {
            return HEAL;
        }
        if (hasPowerType(variant, ForgePowerDefinition.PowerType.ON_BLOCK_POTION)
            || hasPowerType(variant, ForgePowerDefinition.PowerType.ON_BLOCK_KNOCKBACK)
            || hasPowerType(variant, ForgePowerDefinition.PowerType.ON_BLOCK_HEAL)
            || hasDamageReductionAttribute(variant)) {
            return DEFENSIVE;
        }
        return SUCCESS;
    }

    private static boolean hasPowerType(ForgeVariant variant, ForgePowerDefinition.PowerType type) {
        for (ForgePowerDefinition power : variant.getPowers()) {
            if (power.getPowerType() == type) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEffectCandidate(ForgeVariant variant, ForgePowerDefinition.PowerType type,
                                              String effect) {
        for (ForgePowerDefinition power : variant.getPowers()) {
            if (power.getPowerType() == type && hasCandidate(power, effect)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCandidate(ForgePowerDefinition power, String effect) {
        for (String candidate : power.getEffectCandidates()) {
            if (candidate != null && normalize(candidate).equals(effect)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDamageReductionAttribute(ForgeVariant variant) {
        for (ForgeAttributeDefinition attribute : variant.getAttributes()) {
            ForgeAttributeDefinition.AttributeType type = attribute.getType();
            if (type == ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT
                || type == ForgeAttributeDefinition.AttributeType.POISON_DAMAGE_REDUCTION_PERCENT
                || type == ForgeAttributeDefinition.AttributeType.MAGIC_DAMAGE_REDUCTION_PERCENT
                || type == ForgeAttributeDefinition.AttributeType.FALL_DAMAGE_REDUCTION_PERCENT) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
    }
}
