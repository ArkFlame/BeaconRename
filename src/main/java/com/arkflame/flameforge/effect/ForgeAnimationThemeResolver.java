package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyleId;
import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;

import java.util.Locale;
import java.util.Objects;

public final class ForgeAnimationThemeResolver {
    private static final ForgeAnimationTheme BREAK = new ForgeAnimationTheme(
        "break", ParticleStyleId.BREAK);
    private static final ForgeAnimationTheme CURSE = new ForgeAnimationTheme(
        "curse", ParticleStyleId.CURSE);
    private static final ForgeAnimationTheme ELECTRIC = new ForgeAnimationTheme(
        "electric", ParticleStyleId.ELECTRIC);
    private static final ForgeAnimationTheme EXPLOSIVE = new ForgeAnimationTheme(
        "explosive", ParticleStyleId.EXPLOSIVE);
    private static final ForgeAnimationTheme CONTAGION = new ForgeAnimationTheme(
        "contagion", ParticleStyleId.CONTAGION);
    private static final ForgeAnimationTheme POISON = new ForgeAnimationTheme(
        "poison", ParticleStyleId.POISON);
    private static final ForgeAnimationTheme BLEED = new ForgeAnimationTheme(
        "bleed", ParticleStyleId.BLEED);
    private static final ForgeAnimationTheme SWIFT = new ForgeAnimationTheme(
        "swift", ParticleStyleId.SWIFT);
    private static final ForgeAnimationTheme HEAL = new ForgeAnimationTheme(
        "heal", ParticleStyleId.HEAL);
    private static final ForgeAnimationTheme DEFENSIVE = new ForgeAnimationTheme(
        "defensive", ParticleStyleId.DEFENSIVE);
    private static final ForgeAnimationTheme SUCCESS = new ForgeAnimationTheme(
        "success", ParticleStyleId.SUCCESS);

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
