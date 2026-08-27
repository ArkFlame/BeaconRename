package com.arkflame.flameforge.compat.effect.particle.style;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParticleStyleCatalogTest {
    @Test
    void stylesExposeExactElectricNetworkSemanticsAndImmutableCandidates() {
        ParticleStyle style = ParticleStyleCatalog.get(ParticleStyleId.ELECTRIC_NETWORK);

        assertEquals(250, style.getRed());
        assertEquals(204, style.getGreen());
        assertEquals(21, style.getBlue());
        assertEquals(Arrays.asList("ELECTRIC_SPARK", "END_ROD", "ENCHANT", "NOTE", "CRIT"),
            style.getCandidates());
        assertThrows(UnsupportedOperationException.class,
            () -> style.getCandidates().add("FIREWORK"));
    }

    @Test
    void catalogContainsEveryStyleIdAndDefinitionsCannotBeMutated() {
        assertEquals(ParticleStyleId.values().length, ParticleStyleCatalog.all().size());
        assertThrows(UnsupportedOperationException.class,
            () -> ParticleStyleCatalog.styles().clear());
        assertThrows(IllegalArgumentException.class,
            () -> new ParticleStyle(ParticleStyleId.BREAK, 256, 0, 0,
                Arrays.asList("CRIT")));
    }
}
