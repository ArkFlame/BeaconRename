package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForgeAnimationThemeResolverTest {
    private final ForgeAnimationThemeResolver resolver = new ForgeAnimationThemeResolver();

    @Test
    void resolvesElectricThemeForChainOrLightningPowers() {
        ForgeAnimationTheme chain = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.ON_HIT_CHAIN_DAMAGE));
        assertEquals("electric", chain.getId());
        assertPalette(chain, 250, 204, 21);

        ForgeAnimationTheme lightning = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING));
        assertEquals("electric", lightning.getId());
        assertPalette(lightning, 250, 204, 21);
    }

    @Test
    void resolvesExplosiveThemeForExplosivePower() {
        ForgeAnimationTheme theme = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.ON_HIT_EXPLOSIVE));
        assertEquals("explosive", theme.getId());
        assertPalette(theme, 249, 115, 22);
    }

    @Test
    void resolvesContagionThemeForChainPotion() {
        ForgeAnimationTheme theme = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.ON_HIT_CHAIN_POTION));
        assertEquals("contagion", theme.getId());
        assertPalette(theme, 132, 204, 22);
    }

    @Test
    void resolvesPoisonThemeForPotionWithPoisonCandidate() {
        ForgeAnimationTheme theme = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            potionVariant(ForgePowerDefinition.PowerType.ON_HIT_POTION, "poison"));
        assertEquals("poison", theme.getId());
        assertPalette(theme, 34, 197, 94);
    }

    @Test
    void resolvesBleedThemeForBleedPower() {
        ForgeAnimationTheme theme = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.ON_HIT_BLEED));
        assertEquals("bleed", theme.getId());
        assertPalette(theme, 220, 38, 38);
    }

    @Test
    void resolvesSwiftThemeForDashOrPassiveSpeed() {
        ForgeAnimationTheme dash = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH));
        assertEquals("swift", dash.getId());
        assertPalette(dash, 56, 189, 248);

        ForgeAnimationTheme speed = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            potionVariant(ForgePowerDefinition.PowerType.PASSIVE_POTION, "speed"));
        assertEquals("swift", speed.getId());
        assertPalette(speed, 56, 189, 248);
    }

    @Test
    void resolvesHealThemeForHealPowerOrPassiveRegeneration() {
        ForgeAnimationTheme heal = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.ON_HIT_HEAL));
        assertEquals("heal", heal.getId());
        assertPalette(heal, 244, 114, 182);

        ForgeAnimationTheme regen = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            potionVariant(ForgePowerDefinition.PowerType.PASSIVE_POTION, "regeneration"));
        assertEquals("heal", regen.getId());
        assertPalette(regen, 244, 114, 182);
    }

    @Test
    void resolvesDefensiveThemeForOnBlockPowerOrDamageReductionAttribute() {
        ForgeAnimationTheme onBlock = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.ON_BLOCK_POTION));
        assertEquals("defensive", onBlock.getId());
        assertPalette(onBlock, 96, 165, 250);

        ForgeAnimationTheme attribute = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            attributeVariant(ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT));
        assertEquals("defensive", attribute.getId());
        assertPalette(attribute, 96, 165, 250);
    }

    @Test
    void resolvesBreakAndCursePalettesWithoutVariant() {
        ForgeAnimationTheme breakTheme = resolver.resolve(ForgeOutcomeCategory.BREAK, null);
        assertEquals("break", breakTheme.getId());
        assertPalette(breakTheme, 239, 68, 68);

        ForgeAnimationTheme curseTheme = resolver.resolve(ForgeOutcomeCategory.CURSE, null);
        assertEquals("curse", curseTheme.getId());
        assertPalette(curseTheme, 168, 85, 247);
    }

    @Test
    void resolvesGenericSuccessGoldWhenNoPowerMatches() {
        ForgeAnimationTheme theme = resolver.resolve(ForgeOutcomeCategory.SUCCESS,
            variant(ForgePowerDefinition.PowerType.ON_HIT_FIRE));
        assertEquals("success", theme.getId());
        assertPalette(theme, 245, 158, 11);
    }

    @Test
    void successRequiresUsedVariant() {
        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve(ForgeOutcomeCategory.SUCCESS, null));
    }

    private static ForgeVariant variant(ForgePowerDefinition.PowerType... powerTypes) {
        List<ForgePowerDefinition> powers = new ArrayList<>();
        for (ForgePowerDefinition.PowerType type : powerTypes) {
            powers.add(new ForgePowerDefinition("power-" + type.name().toLowerCase(), type, 0, 0,
                BigDecimal.ONE, Collections.emptyList(), 0, 0, 0, BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ZERO, Collections.singletonList(ForgePowerDefinition.ActivationSlot.MAINHAND)));
        }
        return new ForgeVariant("variant", "Variant", Collections.emptyList(), 1.0, null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), powers);
    }

    private static ForgeVariant potionVariant(ForgePowerDefinition.PowerType type, String... effects) {
        List<ForgePowerDefinition> powers = new ArrayList<>();
        powers.add(new ForgePowerDefinition("power-" + type.name().toLowerCase(), type, 0, 0,
            BigDecimal.ONE, Arrays.asList(effects), 0, 0, 0, BigDecimal.ZERO, BigDecimal.ONE,
            BigDecimal.ZERO, Collections.singletonList(ForgePowerDefinition.ActivationSlot.MAINHAND)));
        return new ForgeVariant("variant", "Variant", Collections.emptyList(), 1.0, null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), powers);
    }

    private static ForgeVariant attributeVariant(ForgeAttributeDefinition.AttributeType... types) {
        List<ForgeAttributeDefinition> attributes = new ArrayList<>();
        for (ForgeAttributeDefinition.AttributeType type : types) {
            attributes.add(new ForgeAttributeDefinition("attr-" + type.name().toLowerCase(), type, 0.5));
        }
        return new ForgeVariant("variant", "Variant", Collections.emptyList(), 1.0, null,
            Collections.emptyList(), Collections.emptyList(), attributes, Collections.emptyList());
    }

    private static void assertPalette(ForgeAnimationTheme theme, int red, int green, int blue) {
        assertEquals(red, theme.getAuraRed());
        assertEquals(green, theme.getAuraGreen());
        assertEquals(blue, theme.getAuraBlue());
    }
}
