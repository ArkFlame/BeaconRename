package com.arkflame.flameforge.compat.effect.particle;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleCatalogTest {
    private final ParticleCatalog catalog = new ParticleCatalog();

    @Test
    void preservesRawNameFirstAndAcceptsNamespacedKeys() {
        assertEquals("EXPLOSION_NORMAL", catalog.resolve("EXPLOSION_NORMAL").get(0));
        assertEquals("MINECRAFT:DUST", catalog.resolve("minecraft:dust").get(0));
        assertEquals("UNKNOWN_FUTURE_PARTICLE", catalog.resolve("unknown_future_particle").get(0));
    }

    @Test
    void aliasesKeepFamilyOrderAndRemoveDuplicates() {
        assertEquals(Arrays.asList("redstone", "DUST"), catalog.resolve("redstone"));
        assertEquals(Arrays.asList("block_crack", "BLOCK", "BLOCK_CRACK", "BLOCK_DUST", "TILE_BREAK", "TILE_DUST"),
            catalog.resolve("block_crack"));
        assertEquals(Arrays.asList("item_crack", "ITEM", "ITEM_CRACK", "ITEM_BREAK"),
            catalog.resolve("item_crack"));
    }

    @Test
    void oldAndCurrentNamesRemainCandidates() {
        List<String> explosion = catalog.resolve("EXPLOSION_NORMAL");
        List<String> firework = catalog.resolve("FIREWORKS_SPARK");
        List<String> spell = catalog.resolve("SPELL");

        assertEquals("EXPLOSION_NORMAL", explosion.get(0));
        assertEquals("FIREWORKS_SPARK", firework.get(0));
        assertEquals("SPELL", spell.get(0));
        assertTrue(explosion.contains("EXPLOSION"));
        assertTrue(firework.contains("FIREWORK"));
        assertTrue(spell.contains("EFFECT"));
    }
}
