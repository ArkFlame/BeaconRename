package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.model.PlayerForgeState;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ForgeMenuRegistryTest {

    @Test
    void replaceReturnsDisplacedAndPublishesNewMenu() {
        ForgeMenuRegistry registry = new ForgeMenuRegistry();
        UUID playerId = UUID.randomUUID();
        UUID menuIdA = UUID.randomUUID();
        UUID menuIdB = UUID.randomUUID();
        PlayerForgeState session = PlayerForgeState.of(playerId.toString());

        ForgeMenuContext contextA = new ForgeMenuContext(menuIdA, playerId, "station-1", session, System.currentTimeMillis());
        ForgeMenuContext contextB = new ForgeMenuContext(menuIdB, playerId, "station-1", session, System.currentTimeMillis());

        Optional<ForgeMenuContext> resultA = registry.replace(contextA);
        assertFalse(resultA.isPresent());
        assertEquals(contextA, registry.get(playerId).orElse(null));

        Optional<ForgeMenuContext> resultB = registry.replace(contextB);
        assertTrue(resultB.isPresent());
        assertEquals(contextA, resultB.get());
        assertEquals(contextB, registry.get(playerId).orElse(null));
    }

    @Test
    void staleSameStationMenuCannotRemoveNewerMenu() {
        ForgeMenuRegistry registry = new ForgeMenuRegistry();
        UUID playerId = UUID.randomUUID();
        UUID menuIdA = UUID.randomUUID();
        UUID menuIdB = UUID.randomUUID();
        PlayerForgeState session = PlayerForgeState.of(playerId.toString());

        ForgeMenuContext contextA = new ForgeMenuContext(menuIdA, playerId, "station-1", session, System.currentTimeMillis());
        ForgeMenuContext contextB = new ForgeMenuContext(menuIdB, playerId, "station-1", session, System.currentTimeMillis());

        registry.replace(contextA);
        registry.replace(contextB);

        Optional<ForgeMenuContext> removeResult = registry.removeIfCurrent(playerId, menuIdA);
        assertFalse(removeResult.isPresent());
        assertEquals(contextB, registry.get(playerId).orElse(null));
    }

    @Test
    void drainReturnsEveryContextAndLeavesRegistryEmpty() {
        ForgeMenuRegistry registry = new ForgeMenuRegistry();
        UUID playerX = UUID.randomUUID();
        UUID playerY = UUID.randomUUID();
        UUID menuIdX = UUID.randomUUID();
        UUID menuIdY = UUID.randomUUID();
        PlayerForgeState sessionX = PlayerForgeState.of(playerX.toString());
        PlayerForgeState sessionY = PlayerForgeState.of(playerY.toString());

        ForgeMenuContext contextX = new ForgeMenuContext(menuIdX, playerX, "station-1", sessionX, System.currentTimeMillis());
        ForgeMenuContext contextY = new ForgeMenuContext(menuIdY, playerY, "station-1", sessionY, System.currentTimeMillis());

        registry.replace(contextX);
        registry.replace(contextY);

        assertEquals(2, registry.size());
        assertEquals(2, registry.drain().size());
        assertEquals(0, registry.size());
    }
}
