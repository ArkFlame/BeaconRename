package com.arkflame.flameforge.chance;

import com.arkflame.flameforge.config.BreakSystemConfig;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.TierChances;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OutcomeSelectorFailureTest {
    private final BreakSystemConfig config = BreakSystemConfig.from(ConfigSnapshot.builder()
        .putRoot("break-system.enabled", true)
        .putRoot("break-system.failure-variants.break.enabled", true)
        .putRoot("break-system.failure-variants.curse.enabled", false)
        .putRoot("break-system.failure-variants.shatter.enabled", true)
        .build());

    private final TierChances chances = new TierChances(
        BigDecimal.valueOf(50), BigDecimal.valueOf(25), BigDecimal.valueOf(25),
        Collections.singletonMap("shatter", BigDecimal.valueOf(25)));

    @Test
    void disabledVariantsAreExcludedFromFailureRoll() {
        ForgeOutcomeSelection selection = new OutcomeSelector(new FixedRandom(0.99))
            .rollOutcome(chances, config);

        assertEquals("shatter", selection.getFailureVariantId());
        assertEquals(ForgeOutcomeCategory.BREAK, selection.getCategory());
    }

    @Test
    void successBandReturnsNoFailureVariant() {
        ForgeOutcomeSelection selection = new OutcomeSelector(new FixedRandom(0.01))
            .rollOutcome(chances, config);

        assertEquals(ForgeOutcomeCategory.SUCCESS, selection.getCategory());
        assertNull(selection.getFailureVariantId());
    }

    private static final class FixedRandom implements RandomSource {
        private final double value;
        FixedRandom(double value) { this.value = value; }
        public long nextLong(long bound) { return 0; }
        public double nextDouble() { return value; }
        public double nextDouble(double bound) { return value * bound; }
    }
}
