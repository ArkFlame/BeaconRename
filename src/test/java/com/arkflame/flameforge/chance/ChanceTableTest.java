package com.arkflame.flameforge.chance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChanceTableTest {

    @Test
    void from_validWeights_buildsTableWithCorrectTotal() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3") };
        String[] ids = { "a", "b", "c" };
        int[] orders = { 0, 1, 2 };

        ChanceTable table = ChanceTable.from(weights, ids, orders);

        assertEquals(6_000_000L, table.getTotalMicroWeight());
        assertEquals(3, table.getEntries().size());
    }

    @Test
    void from_residualAdjustedToSum100() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1") };
        String[] ids = { "a", "b", "c" };

        ChanceTable table = ChanceTable.from(weights, ids, null);

        BigDecimal sum = BigDecimal.ZERO;
        for (ChanceEntry e : table.getEntries()) {
            sum = sum.add(e.getDisplayPercentage());
        }
        assertEquals(0, sum.setScale(1, RoundingMode.HALF_UP).compareTo(new BigDecimal("100.0")));
    }

    @Test
    void from_entriesAreInOriginalOrder() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("5"), new BigDecimal("3") };
        String[] ids = { "low", "high", "mid" };
        int[] orders = { 0, 1, 2 };

        ChanceTable table = ChanceTable.from(weights, ids, orders);

        List<ChanceEntry> entries = table.getEntries();
        assertEquals("low", entries.get(0).getOutcomeId());
        assertEquals("high", entries.get(1).getOutcomeId());
        assertEquals("mid", entries.get(2).getOutcomeId());
    }

    @Test
    void from_rejectsNullWeights() {
        BigDecimal[] weights = null;
        String[] ids = { "a" };

        assertThrows(IllegalArgumentException.class, () -> ChanceTable.from(weights, ids, null));
    }

    @Test
    void from_rejectsZeroWeight() {
        BigDecimal[] weights = { new BigDecimal("0") };
        String[] ids = { "a" };

        assertThrows(IllegalArgumentException.class, () -> ChanceTable.from(weights, ids, null));
    }

    @Test
    void from_rejectsNegativeWeight() {
        BigDecimal[] weights = { new BigDecimal("-1") };
        String[] ids = { "a" };

        assertThrows(IllegalArgumentException.class, () -> ChanceTable.from(weights, ids, null));
    }

    @Test
    void from_rejectsWeightScaleExceeding6() {
        BigDecimal[] weights = { new BigDecimal("1.0000001") };
        String[] ids = { "a" };

        assertThrows(IllegalArgumentException.class, () -> ChanceTable.from(weights, ids, null));
    }

    @Test
    void from_rejectsEmptyInput() {
        BigDecimal[] weights = {};
        String[] ids = {};

        assertThrows(IllegalArgumentException.class, () -> ChanceTable.from(weights, ids, null));
    }

    @Test
    void selectIndex_randomMicroZeroSelectsFirstEntry() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3") };
        String[] ids = { "a", "b", "c" };

        ChanceTable table = ChanceTable.from(weights, ids, null);

        int idx = table.selectIndex(0);
        assertEquals(0, idx);
    }

    @Test
    void selectIndex_randomMicroAtLastBoundarySelectsLastEntry() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3") };
        String[] ids = { "a", "b", "c" };

        ChanceTable table = ChanceTable.from(weights, ids, null);
        long lastMicro = table.getTotalMicroWeight() - 1;

        int idx = table.selectIndex(lastMicro);
        assertEquals(2, idx);
    }

    @Test
    void selectIndex_exactCumulativeBoundary() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("1") };
        String[] ids = { "a", "b" };

        ChanceTable table = ChanceTable.from(weights, ids, null);

        int idx0 = table.selectIndex(0);
        int idx1 = table.selectIndex(1_000_000);
        assertEquals(0, idx0);
        assertEquals(1, idx1);
    }

    @Test
    void selectIndex_invalidRandomMicro_throws() {
        BigDecimal[] weights = { new BigDecimal("1") };
        String[] ids = { "a" };

        ChanceTable table = ChanceTable.from(weights, ids, null);

        assertThrows(IllegalArgumentException.class, () -> table.selectIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> table.selectIndex(table.getTotalMicroWeight()));
    }

    @Test
    void select_returnsCorrectEntry() {
        BigDecimal[] weights = { new BigDecimal("5"), new BigDecimal("5") };
        String[] ids = { "first", "second" };

        ChanceTable table = ChanceTable.from(weights, ids, null);
        ChanceEntry entry = table.select(0);

        assertEquals("first", entry.getOutcomeId());
    }

    @Test
    void equals_andHashCode_consistent() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("2") };
        String[] ids = { "a", "b" };

        ChanceTable t1 = ChanceTable.from(weights, ids, null);
        ChanceTable t2 = ChanceTable.from(weights, ids, null);

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void equals_differentWeights_notEqual() {
        BigDecimal[] weights1 = { new BigDecimal("1"), new BigDecimal("2") };
        BigDecimal[] weights2 = { new BigDecimal("1"), new BigDecimal("3") };
        String[] ids = { "a", "b" };

        ChanceTable t1 = ChanceTable.from(weights1, ids, null);
        ChanceTable t2 = ChanceTable.from(weights2, ids, null);

        assertNotEquals(t1, t2);
    }
}
