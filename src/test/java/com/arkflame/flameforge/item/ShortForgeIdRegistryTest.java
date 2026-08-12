package com.arkflame.flameforge.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortForgeIdRegistryTest {

    @Test
    void generatedIdsAreStableValidAndNormallyDistinct() {
        ShortForgeIdRegistry registry = new ShortForgeIdRegistry();
        UUID firstForge = UUID.randomUUID();
        UUID secondForge = UUID.randomUUID();

        String first = registry.claimOrGenerate(firstForge);

        assertEquals(first, registry.claimOrGenerate(firstForge));
        assertTrue(first.matches("[A-HJ-NP-Z2-9]{8}"));
        assertNotEquals(first, registry.claimOrGenerate(secondForge));
    }

    @Test
    void collisionsRetryAndExhaustionFails() {
        ShortForgeIdRegistry retrying = new ShortForgeIdRegistry((forgeId, attempt) ->
                "AAAAAAA" + (attempt == 0 ? "A" : "B"));
        UUID firstForge = UUID.randomUUID();
        UUID secondForge = UUID.randomUUID();

        assertEquals("AAAAAAAA", retrying.claimOrGenerate(firstForge));
        assertEquals("AAAAAAAB", retrying.claimOrGenerate(secondForge));

        ShortForgeIdRegistry exhausted = new ShortForgeIdRegistry((forgeId, attempt) -> "AAAAAAAA");
        exhausted.claimOrGenerate(UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> exhausted.claimOrGenerate(UUID.randomUUID()));
    }
}
