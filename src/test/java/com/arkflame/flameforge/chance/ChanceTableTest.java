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
    void fromBuildsOrderedTableAndAdjustsResidual() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1") };
        String[] ids = { "a", "b", "c" };
        int[] orders = { 0, 1, 2 };

        ChanceTable table = ChanceTable.from(weights, ids, orders);

        assertEquals(3_000_000L, table.getTotalMicroWeight());
        assertEquals(3, table.getEntries().size());

        List<ChanceEntry> entries = table.getEntries();
        assertEquals("a", entries.get(0).getOutcomeId());
        assertEquals("b", entries.get(1).getOutcomeId());
        assertEquals("c", entries.get(2).getOutcomeId());

        BigDecimal sum = BigDecimal.ZERO;
        for (ChanceEntry e : table.getEntries()) {
            sum = sum.add(e.getDisplayPercentage());
        }
        assertEquals(0, sum.setScale(1, RoundingMode.HALF_UP).compareTo(new BigDecimal("100.0")));
    }

    @Test
    void fromRejectsNullEmptyNonPositiveAndExcessScaleWeights() {
        String[] cases = { "null", "empty", "zero", "negative", "excessScale" };
        String[] ids = { "a" };

        for (String caseName : cases) {
            switch (caseName) {
                case "null":
                    assertThrows(IllegalArgumentException.class,
                        () -> ChanceTable.from((BigDecimal[]) null, ids, null));
                    break;
                case "empty":
                    assertThrows(IllegalArgumentException.class,
                        () -> ChanceTable.from(new BigDecimal[] {}, new String[] {}, null));
                    break;
                case "zero":
                    assertThrows(IllegalArgumentException.class,
                        () -> ChanceTable.from(new BigDecimal[] { BigDecimal.ZERO }, ids, null));
                    break;
                case "negative":
                    assertThrows(IllegalArgumentException.class,
                        () -> ChanceTable.from(new BigDecimal[] { new BigDecimal("-1") }, ids, null));
                    break;
                case "excessScale":
                    assertThrows(IllegalArgumentException.class,
                        () -> ChanceTable.from(new BigDecimal[] { new BigDecimal("1.0000001") }, ids, null));
                    break;
            }
        }
    }

    @Test
    void selectIndexAcceptsFirstLastAndExactBoundary() {
        BigDecimal[] weights = { new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3") };
        String[] ids = { "a", "b", "c" };

        ChanceTable table = ChanceTable.from(weights, ids, null);

        assertEquals(0, table.selectIndex(0));
        assertEquals(2, table.selectIndex(table.getTotalMicroWeight() - 1));

        int idx0 = table.selectIndex(0);
        int idx1 = table.selectIndex(1_000_000);
        assertEquals(0, idx0);
        assertEquals(1, idx1);
    }

    @Test
    void selectIndexRejectsOutOfRangeMicroValue() {
        BigDecimal[] weights = { new BigDecimal("1") };
        String[] ids = { "a" };

        ChanceTable table = ChanceTable.from(weights, ids, null);

        assertThrows(IllegalArgumentException.class, () -> table.selectIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> table.selectIndex(table.getTotalMicroWeight()));
    }

    @Test
    void selectReturnsEntryAtSelectedIndex() {
        BigDecimal[] weights = { new BigDecimal("5"), new BigDecimal("5") };
        String[] ids = { "first", "second" };

        ChanceTable table = ChanceTable.from(weights, ids, null);
        ChanceEntry entry = table.select(0);

        assertEquals("first", entry.getOutcomeId());
    }

    @Test
    void equalsHashCodeMatchEquivalentWeights() {
        BigDecimal[] weights1 = { new BigDecimal("1"), new BigDecimal("2") };
        BigDecimal[] weights2 = { new BigDecimal("1"), new BigDecimal("2") };
        String[] ids = { "a", "b" };

        ChanceTable t1 = ChanceTable.from(weights1, ids, null);
        ChanceTable t2 = ChanceTable.from(weights2, ids, null);

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void differentWeightsAreNotEqual() {
        BigDecimal[] weights1 = { new BigDecimal("1"), new BigDecimal("2") };
        BigDecimal[] weights2 = { new BigDecimal("1"), new BigDecimal("3") };
        String[] ids = { "a", "b" };

        ChanceTable t1 = ChanceTable.from(weights1, ids, null);
        ChanceTable t2 = ChanceTable.from(weights2, ids, null);

        assertNotEquals(t1, t2);
    }
}
