package com.arkflame.flameforge.chance;

import com.arkflame.flameforge.model.OutcomeDefinition;
import com.arkflame.flameforge.model.OutcomeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutcomeSelectorTest {

    private static final BigDecimal W1 = new BigDecimal("1");
    private static final BigDecimal W2 = new BigDecimal("2");

    private OutcomeDefinition def(String id, BigDecimal weight) {
        return OutcomeDefinition.of(id, OutcomeType.BREAK, weight, null, Collections.emptyList(), 0);
    }

    @Test
    void buildChanceTable_nullOutcomes_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));

        assertThrows(IllegalArgumentException.class, () -> selector.buildChanceTable(null));
    }

    @Test
    void buildChanceTable_emptyOutcomes_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));

        assertThrows(IllegalArgumentException.class, () -> selector.buildChanceTable(Collections.emptyList()));
    }

    @Test
    void buildChanceTable_singleOutcome_buildsTable() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = Collections.singletonList(def("only", W1));

        ChanceTable table = selector.buildChanceTable(outcomes);

        assertEquals(1, table.getEntries().size());
        assertEquals("only", table.getEntries().get(0).getOutcomeId());
    }

    @Test
    void select_nullOutcomes_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));

        assertThrows(IllegalArgumentException.class, () ->
            selector.select(null, null, null, null, null, null, null));
    }

    @Test
    void select_emptyOutcomes_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));

        assertThrows(IllegalArgumentException.class, () ->
            selector.select(Collections.emptyList(), null, null, null, null, null, null));
    }

    @Test
    void select_nullFilters_passesAll() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeDefinition selected = selector.select(outcomes, null, null, null, null, null, null);

        assertNotNull(selected);
        assertEquals("a", selected.getId());
    }

    @Test
    void select_materialFilterExcludesAll_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeSelector.MaterialFilter filter = od -> false;

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            selector.select(outcomes, filter, null, null, null, null, null));

        assertTrue(ex.getMessage().contains("No outcomes passed filters"));
    }

    @Test
    void select_pluginFilterExcludesAll_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeSelector.PluginFilter filter = od -> false;

        assertThrows(IllegalStateException.class, () ->
            selector.select(outcomes, null, filter, null, null, null, null));
    }

    @Test
    void select_capabilityFilterExcludesAll_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeSelector.CapabilityFilter filter = od -> false;

        assertThrows(IllegalStateException.class, () ->
            selector.select(outcomes, null, null, filter, null, null, null));
    }

    @Test
    void select_playerFilterExcludesAll_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeSelector.PlayerFilter filter = od -> false;

        assertThrows(IllegalStateException.class, () ->
            selector.select(outcomes, null, null, null, filter, null, null));
    }

    @Test
    void select_catalystFilterExcludesAll_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeSelector.CatalystFilter filter = od -> false;

        assertThrows(IllegalStateException.class, () ->
            selector.select(outcomes, null, null, null, null, filter, null));
    }

    @Test
    void select_wardFilterExcludesAll_throws() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeSelector.WardFilter filter = od -> false;

        assertThrows(IllegalStateException.class, () ->
            selector.select(outcomes, null, null, null, null, null, filter));
    }

    @Test
    void select_materialFilterExcludesOne_selectsOther() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeSelector.MaterialFilter filter = od -> od.getId().equals("b");

        OutcomeDefinition selected = selector.select(outcomes, filter, null, null, null, null, null);

        assertEquals("b", selected.getId());
    }

    @Test
    void select_multipleFilters_allMustPass() {
        OutcomeSelector selector = new OutcomeSelector(new DeterministicRandom(0));
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W2));

        OutcomeSelector.MaterialFilter matFilter = od -> od.getId().equals("a");
        OutcomeSelector.PluginFilter pluginFilter = od -> od.getId().equals("a");

        OutcomeDefinition selected = selector.select(outcomes, matFilter, pluginFilter, null, null, null, null);

        assertEquals("a", selected.getId());
    }

    @Test
    void select_deterministicFixedRandom_sameOutcome() {
        DeterministicRandom rng = new DeterministicRandom(500_000); // mid-point for 1M total
        OutcomeSelector selector = new OutcomeSelector(rng);
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W1));

        OutcomeDefinition first = selector.select(outcomes, null, null, null, null, null, null);
        OutcomeDefinition second = selector.select(outcomes, null, null, null, null, null, null);

        assertEquals(first.getId(), second.getId());
    }

    @Test
    void select_lastBoundaryValue_selectsLastEntry() {
        DeterministicRandom rng = new DeterministicRandom(1_999_999); // last micro for 2M total
        OutcomeSelector selector = new OutcomeSelector(rng);
        List<OutcomeDefinition> outcomes = java.util.Arrays.asList(def("a", W1), def("b", W1));

        OutcomeDefinition selected = selector.select(outcomes, null, null, null, null, null, null);

        assertEquals("b", selected.getId());
    }

    private static class DeterministicRandom implements RandomSource {
        private final long value;

        DeterministicRandom(long value) { this.value = value; }

        @Override
        public long nextLong(long bound) {
            return value % bound;
        }
    }
}
