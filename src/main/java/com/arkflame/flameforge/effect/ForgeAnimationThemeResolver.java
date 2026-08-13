package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;

import java.util.Objects;

public final class ForgeAnimationThemeResolver {
    private static final ForgeAnimationTheme SUCCESS = new ForgeAnimationTheme(
        "success", 255, 190, 45, 255, 245, 180, "flame", "firework");
    private static final ForgeAnimationTheme BREAK = new ForgeAnimationTheme(
        "break", 235, 55, 45, 255, 115, 75, "smoke", "crit");
    private static final ForgeAnimationTheme CURSE = new ForgeAnimationTheme(
        "curse", 145, 45, 220, 220, 120, 255, "portal", "spell");

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
                return SUCCESS;
        }
    }
}
