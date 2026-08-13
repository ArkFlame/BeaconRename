package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForgeAnimationThemeResolverTest {
    private final ForgeAnimationThemeResolver resolver = new ForgeAnimationThemeResolver();

    @Test
    void resolvesDistinctOutcomeThemes() {
        ForgeVariant variant = new ForgeVariant("variant", "Variant", Collections.emptyList(), 1.0, null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertEquals("success", resolver.resolve(ForgeOutcomeCategory.SUCCESS, variant).getId());
        assertEquals("break", resolver.resolve(ForgeOutcomeCategory.BREAK, null).getId());
        assertEquals("curse", resolver.resolve(ForgeOutcomeCategory.CURSE, null).getId());
    }

    @Test
    void successRequiresUsedVariant() {
        assertThrows(IllegalArgumentException.class,
            () -> resolver.resolve(ForgeOutcomeCategory.SUCCESS, null));
    }
}
