package com.arkflame.flameforge.chance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChanceTableTest {

    @Test
    void buildAndSelectCoversValidWeightedTable() {
        ChanceTable table = ChanceTable.from(
            new BigDecimal[] {new BigDecimal("1.5"), new BigDecimal("2.5")},
            new String[] {"first", "second"},
            null);

        assertFalse(table.getEntries().isEmpty());
        assertTrue(table.getTotalMicroWeight() > 0);
        assertEquals("first", table.select(0).getOutcomeId());
        assertEquals("second", table.select(table.getTotalMicroWeight() - 1).getOutcomeId());
    }

    @Test
    void invalidWeightsAndOutOfRangeRollAreRejected() {
        String[] ids = {"only"};

        assertThrows(IllegalArgumentException.class,
            () -> ChanceTable.from(null, ids, null));
        assertThrows(IllegalArgumentException.class,
            () -> ChanceTable.from(new BigDecimal[0], new String[0], null));
        assertThrows(IllegalArgumentException.class,
            () -> ChanceTable.from(new BigDecimal[] {BigDecimal.ZERO}, ids, null));
        assertThrows(IllegalArgumentException.class,
            () -> ChanceTable.from(new BigDecimal[] {new BigDecimal("-1")}, ids, null));
        assertThrows(IllegalArgumentException.class,
            () -> ChanceTable.from(new BigDecimal[] {new BigDecimal("1.0000001")}, ids, null));

        ChanceTable table = ChanceTable.from(new BigDecimal[] {new BigDecimal("1")}, ids, null);
        assertThrows(IllegalArgumentException.class, () -> table.selectIndex(-1));
        assertThrows(IllegalArgumentException.class,
            () -> table.selectIndex(table.getTotalMicroWeight()));
    }
}
