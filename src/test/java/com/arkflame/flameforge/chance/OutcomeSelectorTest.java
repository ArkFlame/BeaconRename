package com.arkflame.flameforge.chance;

import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierChances;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutcomeSelectorTest {

    private ForgeVariant variant(String id, double weight) {
        return new ForgeVariant(id, "", Collections.emptyList(), weight, null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList());
    }

    @Test
    void categoryRollCoversSuccessBreakAndCurseBands() {
        TierChances chances = new TierChances(50.0, 25.0, 25.0);

        assertEquals(ForgeOutcomeCategory.SUCCESS,
            new OutcomeSelector(new DeterministicRandom(0)).rollCategory(chances));
        assertEquals(ForgeOutcomeCategory.BREAK,
            new OutcomeSelector(new DeterministicRandom(500000)).rollCategory(chances));
        assertEquals(ForgeOutcomeCategory.CURSE,
            new OutcomeSelector(new DeterministicRandom(900000)).rollCategory(chances));
    }

    @Test
    void variantSelectionUsesWeightsAndRejectsUnusableVariants() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        assertNull(selector.selectVariant(Collections.emptyList()));

        List<ForgeVariant> unusable = Arrays.asList(variant("zero-a", 0.0), variant("zero-b", 0.0));
        assertThrows(IllegalArgumentException.class, () -> selector.selectVariant(unusable));

        ForgeVariant selected = selector.selectVariant(Arrays.asList(
            variant("usable-a", 1.0), variant("usable-b", 2.0)));
        assertEquals("usable-a", selected.getId());
    }

    @Test
    void deterministicRandomProducesStableSelections() {
        List<ForgeVariant> variants = Arrays.asList(
            variant("first", 1.0), variant("second", 3.0));
        OutcomeSelector first = new OutcomeSelector(new DeterministicRandom(500000));
        OutcomeSelector second = new OutcomeSelector(new DeterministicRandom(500000));

        assertEquals(first.selectVariant(variants).getId(), second.selectVariant(variants).getId());
        TierChances chances = new TierChances(60.0, 20.0, 20.0);
        assertEquals(first.rollCategory(chances), second.rollCategory(chances));
    }

    private static final class DeterministicRandom implements RandomSource {
        private final long value;

        private DeterministicRandom(long value) {
            this.value = value;
        }

        @Override
        public long nextLong(long bound) {
            return value % bound;
        }

        @Override
        public double nextDouble() {
            return (double) (value % 1000000) / 1000000.0;
        }

        @Override
        public double nextDouble(double bound) {
            return nextDouble() * bound;
        }
    }
}
