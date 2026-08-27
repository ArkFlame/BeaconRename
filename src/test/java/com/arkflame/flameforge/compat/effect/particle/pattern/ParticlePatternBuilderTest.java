package com.arkflame.flameforge.compat.effect.particle.pattern;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParticlePatternBuilderTest {
    @Test
    void lineAndPolylinePreserveOrderedGeometry() {
        ParticlePoint start = new ParticlePoint(0, 0, 0);
        ParticlePoint middle = new ParticlePoint(1, 0, 0);
        ParticlePoint end = new ParticlePoint(2, 0, 0);

        assertEquals(Arrays.asList(start, new ParticlePoint(1, 0, 0), end),
            ParticlePatternBuilder.line(start, end, 3).getPoints());
        assertEquals(Arrays.asList(start, middle, end),
            ParticlePatternBuilder.polyline(Arrays.asList(start, middle, end), 2).getPoints());
    }

    @Test
    void patternsHaveExpectedCountsFiniteValuesAndCaps() {
        ParticlePattern circle = ParticlePatternBuilder.circle(new ParticlePoint(0, 0, 0), 2, 8);
        ParticlePattern helix = ParticlePatternBuilder.helix(new ParticlePoint(0, 0, 0), 1, 3, 2, 6);
        ParticlePattern star = ParticlePatternBuilder.star(new ParticlePoint(0, 0, 0), 2, 1, 10);

        assertEquals(8, circle.size());
        assertEquals(12, helix.size());
        assertEquals(10, star.size());
        for (ParticlePoint point : helix.getPoints()) {
            assertFalse(Double.isNaN(point.x()));
            assertFalse(Double.isInfinite(point.x()));
        }
        assertThrows(IllegalArgumentException.class,
            () -> ParticlePatternBuilder.line(new ParticlePoint(0, 0, 0),
                new ParticlePoint(1, 1, 1), ParticlePattern.MAX_POINTS + 1));
    }
}
