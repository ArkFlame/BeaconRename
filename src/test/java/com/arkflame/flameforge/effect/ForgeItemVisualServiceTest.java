package com.arkflame.flameforge.effect;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeItemVisualServiceTest {
    private Logger logger;
    private RecordingPacketTransport transport;
    private RecordingPacketFactory packetFactory;
    private AtomicInteger entityIdCounter;
    private ForgeItemVisualService service;
    private Player viewer;
    private org.bukkit.Location bukkitLocation;
    private org.bukkit.inventory.ItemStack bukkitItem;

    static final class PacketMarker {
        final String type;
        final int entityId;
        final Object data;

        PacketMarker(String type, int entityId, Object data) {
            this.type = type;
            this.entityId = entityId;
            this.data = data;
        }
    }

    static class RecordingPacketTransport implements ForgeItemVisualService.PacketTransport {
        private final List<Object> sentPackets = new ArrayList<>();
        private String failurePrefix;

        @Override
        public void send(Player player, Object packet) {
            if (failurePrefix != null && packet.toString().startsWith(failurePrefix)) {
                throw new RuntimeException("packet send failed");
            }
            sentPackets.add(packet);
        }

        List<Object> getSentPackets() {
            return sentPackets;
        }

        void failOn(String prefix) {
            failurePrefix = prefix;
        }
    }

    static final class RecordingPacketFactory implements ForgeItemVisualService.PacketFactory {
        private final List<PacketMarker> markers = new ArrayList<>();

        List<PacketMarker> getMarkers() {
            return markers;
        }

        @Override
        public ForgeItemVisualService.SpawnPacketBundle createSpawnPackets(int entityId, UUID entityUuid,
                                                                            org.bukkit.Location bukkitLocation,
                                                                            org.bukkit.inventory.ItemStack bukkitItem) {
            markers.add(new PacketMarker("SPAWN", entityId, bukkitLocation));
            markers.add(new PacketMarker("METADATA", entityId, bukkitItem));
            return new ForgeItemVisualService.SpawnPacketBundle(
                new Object() { @Override public String toString() { return "SpawnEntity:" + entityId; } },
                new Object() { @Override public String toString() { return "Metadata:" + entityId; } }
            );
        }

        @Override
        public Object createTeleportPacket(int entityId, org.bukkit.Location bukkitLocation) {
            markers.add(new PacketMarker("TELEPORT", entityId, bukkitLocation));
            return new Object() { @Override public String toString() { return "Teleport:" + entityId; } };
        }

        @Override
        public Object createDestroyPacket(int entityId) {
            markers.add(new PacketMarker("DESTROY", entityId, null));
            return new Object() { @Override public String toString() { return "Destroy:" + entityId; } };
        }
    }

    @BeforeEach
    void setUp() {
        logger = mock(Logger.class);
        transport = new RecordingPacketTransport();
        packetFactory = new RecordingPacketFactory();
        entityIdCounter = new AtomicInteger(1000);
        service = new ForgeItemVisualService(logger, transport, packetFactory, entityIdCounter::getAndIncrement);

        viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());

        bukkitLocation = mock(org.bukkit.Location.class);
        when(bukkitLocation.getX()).thenReturn(10.0);
        when(bukkitLocation.getY()).thenReturn(64.0);
        when(bukkitLocation.getZ()).thenReturn(20.0);
        when(bukkitLocation.getYaw()).thenReturn(0.0f);
        when(bukkitLocation.getPitch()).thenReturn(0.0f);
        when(bukkitLocation.getWorld()).thenReturn(null);

        bukkitItem = mock(org.bukkit.inventory.ItemStack.class);
        when(bukkitItem.getType()).thenReturn(org.bukkit.Material.DIAMOND);
        when(bukkitItem.getAmount()).thenReturn(1);
    }

    @Test
    void spawnMoveRefreshDestroyLifecycleUsesSameFakeEntity() {
        assertTrue(service.spawn("tx-lifecycle", viewer, bukkitItem, bukkitLocation));
        assertTrue(service.move("tx-lifecycle", bukkitLocation));
        assertTrue(service.refreshMetadata("tx-lifecycle"));
        service.destroy("tx-lifecycle");

        PacketMarker spawn = marker("SPAWN");
        PacketMarker move = marker("TELEPORT");
        PacketMarker destroy = marker("DESTROY");
        assertNotNull(spawn);
        assertNotNull(move);
        assertNotNull(destroy);
        assertEquals(spawn.entityId, move.entityId);
        assertEquals(spawn.entityId, destroy.entityId);
        assertFalse(transport.getSentPackets().isEmpty());
    }

    @Test
    void metadataSelectionSupportsLegacyAndModernProtocolFamilies() {
        assertEquals(10, ForgeItemVisualService.itemStackMetadataIndex(ClientVersion.V_1_8));
        assertEquals(8, ForgeItemVisualService.itemStackMetadataIndex(ClientVersion.V_1_17));
    }

    @Test
    void invalidInputsAndUnknownTransactionsFailSafely() {
        assertFalse(service.spawn(null, viewer, bukkitItem, bukkitLocation));
        assertFalse(service.spawn("tx-invalid-viewer", null, bukkitItem, bukkitLocation));
        assertFalse(service.spawn("tx-invalid-item", viewer, null, bukkitLocation));
        assertFalse(service.spawn("tx-invalid-location", viewer, bukkitItem, null));

        when(bukkitItem.getType()).thenReturn(org.bukkit.Material.AIR);
        assertFalse(service.spawn("tx-air", viewer, bukkitItem, bukkitLocation));
        when(bukkitItem.getType()).thenReturn(org.bukkit.Material.DIAMOND);
        when(bukkitItem.getAmount()).thenReturn(0);
        assertFalse(service.spawn("tx-empty", viewer, bukkitItem, bukkitLocation));

        assertFalse(service.move("unknown", bukkitLocation));
        assertFalse(service.refreshMetadata("unknown"));
    }

    @Test
    void packetConstructionOrSendFailureIsContainedAndCleanedUp() {
        transport.failOn("Metadata:");

        assertDoesNotThrow(() -> assertFalse(service.spawn("tx-spawn-failure", viewer, bukkitItem, bukkitLocation)));
        assertNotNull(marker("DESTROY"));

        RecordingPacketTransport moveTransport = new RecordingPacketTransport();
        RecordingPacketFactory moveFactory = new RecordingPacketFactory();
        ForgeItemVisualService moveService = new ForgeItemVisualService(
            logger, moveTransport, moveFactory, entityIdCounter::getAndIncrement);
        assertTrue(moveService.spawn("tx-move-failure", viewer, bukkitItem, bukkitLocation));
        moveTransport.failOn("Teleport:");

        assertFalse(moveService.move("tx-move-failure", bukkitLocation));
        assertNotNull(findMarker(moveFactory.getMarkers(), "DESTROY"));
        assertFalse(moveService.refreshMetadata("tx-move-failure"));
    }

    private PacketMarker marker(String type) {
        return findMarker(packetFactory.getMarkers(), type);
    }

    private PacketMarker findMarker(List<PacketMarker> markers, String type) {
        for (PacketMarker marker : markers) {
            if (type.equals(marker.type)) {
                return marker;
            }
        }
        return null;
    }
}
