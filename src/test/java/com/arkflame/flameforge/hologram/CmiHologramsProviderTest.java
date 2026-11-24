package com.arkflame.flameforge.hologram;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CmiHologramsProviderTest {

    private static final Logger LOGGER = Logger.getLogger("test");

    @Test
    void upsertCreatesHologramWhenAbsent() {
        FakeBridge bridge = new FakeBridge(true);
        CmiHologramsProvider provider = new CmiHologramsProvider(bridge, LOGGER);
        ForgeHologram hologram = hologram("forge-id_hologram", "one", "two");

        provider.upsert(hologram);

        assertEquals(1, bridge.creates);
        assertEquals(0, bridge.updates);
        assertTrue(bridge.exists);
        assertEquals(Arrays.asList("one", "two"), bridge.lines);
    }

    @Test
    void upsertUpdatesLinesWhenHologramExists() {
        FakeBridge bridge = new FakeBridge(true);
        bridge.exists = true;
        CmiHologramsProvider provider = new CmiHologramsProvider(bridge, LOGGER);
        ForgeHologram hologram = hologram("forge-id_hologram", "updated");

        provider.upsert(hologram);

        assertEquals(0, bridge.creates);
        assertEquals(1, bridge.updates);
        assertEquals(Collections.singletonList("updated"), bridge.lines);
    }

    @Test
    void removeDeletesExistingHologramAndSkipsMissingOne() {
        FakeBridge bridge = new FakeBridge(true);
        CmiHologramsProvider provider = new CmiHologramsProvider(bridge, LOGGER);

        provider.remove("missing");
        assertEquals(0, bridge.deletes);

        bridge.exists = true;
        provider.remove("forge-id_hologram");
        assertEquals(1, bridge.deletes);
        assertFalse(bridge.exists);
    }

    @Test
    void unavailableProviderIgnoresUpsertAndRemove() {
        FakeBridge bridge = new FakeBridge(false);
        CmiHologramsProvider provider = new CmiHologramsProvider(bridge, LOGGER);

        provider.upsert(hologram("forge-id_hologram", "line"));
        provider.remove("forge-id_hologram");

        assertEquals(0, bridge.creates);
        assertEquals(0, bridge.updates);
        assertEquals(0, bridge.deletes);
    }

    @Test
    void missingCmiClassesDegradeGracefullyWithoutThrowing() {
        CmiHologramsProvider provider = new CmiHologramsProvider(mock(Plugin.class), LOGGER);

        assertFalse(provider.isAvailable());
        assertNotNull(provider.getUnavailableReason());
        assertFalse(provider.getUnavailableReason().trim().isEmpty());
        provider.upsert(hologram("forge-id_hologram", "line"));
        provider.remove("forge-id_hologram");
    }

    private static ForgeHologram hologram(String id, String... legacyLines) {
        Location location = new Location(mock(World.class), 10, 64, 10);
        List<String> lines = Arrays.asList(legacyLines);
        return new ForgeHologram(id, location, lines, lines, true);
    }

    private static final class FakeBridge implements CmiHologramBridge {
        private final boolean available;
        private boolean exists;
        private List<String> lines = Collections.emptyList();
        private int creates;
        private int updates;
        private int deletes;

        private FakeBridge(boolean available) {
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String getUnavailableReason() {
            return available ? null : "fake CMI unavailable";
        }

        @Override
        public String getVersion() {
            return "test";
        }

        @Override
        public boolean exists(String hologramId) {
            return exists;
        }

        @Override
        public void create(ForgeHologram hologram) {
            creates++;
            exists = true;
            lines = hologram.getLegacyLines();
        }

        @Override
        public void updateLines(ForgeHologram hologram) {
            updates++;
            lines = hologram.getLegacyLines();
        }

        @Override
        public void delete(String hologramId) {
            deletes++;
            exists = false;
        }
    }
}
