package com.arkflame.flameforge.chance;

import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.OutcomeDefinition;
import com.arkflame.flameforge.model.OutcomeType;
import com.arkflame.flameforge.model.TierChances;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutcomeSelectorTest {

    private static final BigDecimal W1 = new BigDecimal("1");
    private static final BigDecimal W2 = new BigDecimal("2");

    private ChanceEntry entry(String id, BigDecimal weight, int yamlOrder) {
        return ChanceEntry.of(id, weight, (long) (weight.doubleValue() * 1_000_000), weight, yamlOrder);
    }

    private ForgeVariant variant(String id, double weight) {
        return new ForgeVariant(id, "", Collections.emptyList(), weight, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    @Test
    void buildChanceTableRejectsNullOrEmptyAndAcceptsSingleOutcome() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        for (String caseName : new String[] {"null", "empty", "single"}) {
            switch (caseName) {
                case "null":
                    assertThrows(IllegalArgumentException.class, () -> selector.buildChanceTable(null));
                    break;
                case "empty":
                    assertThrows(IllegalArgumentException.class,
                        () -> selector.buildChanceTable(Collections.emptyList()));
                    break;
                case "single":
                    List<ChanceEntry> single = Collections.singletonList(entry("only", W1, 0));
                    ChanceTable table = selector.buildChanceTable(single);
                    assertEquals(1, table.getEntries().size());
                    assertEquals("only", table.getEntries().get(0).getOutcomeId());
                    break;
            }
        }
    }

    @Test
    void rollCategoryReturnsBreakWhenChancesAreZero() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        TierChances zeroChances = new TierChances(0, 0, 0);
        ForgeOutcomeCategory category = selector.rollCategory(zeroChances);
        assertEquals(ForgeOutcomeCategory.BREAK, category);
    }

    @Test
    void rollCategoryReturnsSuccessWhenRollIsBelowSuccessThreshold() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        TierChances chances = new TierChances(50.0, 25.0, 25.0);
        ForgeOutcomeCategory category = selector.rollCategory(chances);
        assertEquals(ForgeOutcomeCategory.SUCCESS, category);
    }

    @Test
    void rollCategoryReturnsBreakWhenRollIsBetweenSuccessAndBreakThreshold() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(500000));
        TierChances chances = new TierChances(50.0, 25.0, 25.0);
        ForgeOutcomeCategory category = selector.rollCategory(chances);
        assertEquals(ForgeOutcomeCategory.BREAK, category);
    }

    @Test
    void rollCategoryReturnsCurseWhenRollIsAboveBreakThreshold() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(900000));
        TierChances chances = new TierChances(50.0, 25.0, 25.0);
        ForgeOutcomeCategory category = selector.rollCategory(chances);
        assertEquals(ForgeOutcomeCategory.CURSE, category);
    }

    @Test
    void selectVariantReturnsNullForEmptyList() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        ForgeVariant variant = selector.selectVariant(Collections.emptyList());
        assertNull(variant);
    }

    @Test
    void selectVariantRejectsAllZeroWeights() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        ForgeVariant variant1 = variant("v1", 0.0);
        ForgeVariant variant2 = variant("v2", 0.0);
        List<ForgeVariant> variants = java.util.Arrays.asList(variant1, variant2);
        assertThrows(IllegalArgumentException.class, () -> selector.selectVariant(variants));
    }

    @Test
    void selectVariantSelectsBasedOnWeightDistribution() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        ForgeVariant variant1 = variant("v1", 1.0);
        ForgeVariant variant2 = variant("v2", 1.0);
        List<ForgeVariant> variants = java.util.Arrays.asList(variant1, variant2);
        ForgeVariant selected = selector.selectVariant(variants);
        assertNotNull(selected);
        assertTrue(selected.getId().equals("v1") || selected.getId().equals("v2"));
    }

    @Test
    void deterministicRandomProducesConsistentResults() {
        DeterministicRandom rng = new DeterministicRandom(500_000);
        OutcomeSelector selector = new OutcomeSelector(rng);
        TierChances chances = new TierChances(50.0, 50.0, 0.0);
        ForgeOutcomeCategory first = selector.rollCategory(chances);
        ForgeOutcomeCategory second = selector.rollCategory(chances);
        assertEquals(first, second);
    }

    @Test
    void selectVariantWithSingleEntryReturnsThatEntry() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        ForgeVariant onlyVariant = variant("only", 1.0);
        List<ForgeVariant> variants = Collections.singletonList(onlyVariant);
        ForgeVariant selected = selector.selectVariant(variants);
        assertEquals("only", selected.getId());
    }

    @Test
    void selectVariantReturnsLastVariantWhenRollExceedsAllWeights() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(5_999_999));
        ForgeVariant light = variant("light", 1.0);
        ForgeVariant medium = variant("medium", 2.0);
        ForgeVariant heavy = variant("heavy", 3.0);
        List<ForgeVariant> variants = java.util.Arrays.asList(light, medium, heavy);
        ForgeVariant selected = selector.selectVariant(variants);
        assertNotNull(selected);
        assertEquals("heavy", selected.getId());
    }

    @Test
    void rollCategoryWithExtremeChancesRemainsWithinBounds() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        TierChances extremeChances = new TierChances(99.9, 0.05, 0.05);
        ForgeOutcomeCategory category = selector.rollCategory(extremeChances);
        assertEquals(ForgeOutcomeCategory.SUCCESS, category);
    }

    private static class DeterministicRandom implements RandomSource {
        private final long value;

        DeterministicRandom(long value) { this.value = value; }

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