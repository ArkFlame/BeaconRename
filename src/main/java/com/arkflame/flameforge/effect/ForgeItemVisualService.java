package com.arkflame.flameforge.effect;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityMetadataProvider;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ForgeItemVisualService {

    interface PacketTransport {
        void send(Player player, Object packet);
    }

    interface PacketFactory {
        SpawnPacketBundle createSpawnPackets(int entityId, UUID entityUuid, org.bukkit.Location bukkitLocation, org.bukkit.inventory.ItemStack bukkitItem);
        Object createTeleportPacket(int entityId, org.bukkit.Location bukkitLocation);
        Object createDestroyPacket(int entityId);
    }

    public static final class SpawnPacketBundle {
        private final Object spawnPacket;
        private final Object metadataPacket;

        SpawnPacketBundle(Object spawnPacket, Object metadataPacket) {
            this.spawnPacket = spawnPacket;
            this.metadataPacket = metadataPacket;
        }

        public Object getSpawnPacket() {
            return spawnPacket;
        }

        public Object getMetadataPacket() {
            return metadataPacket;
        }
    }

    private final Logger logger;
    private final PacketTransport transport;
    private final PacketFactory packetFactory;
    private final IntSupplier entityIds;
    private final ConcurrentHashMap<String, FakeItemState> active = new ConcurrentHashMap<>();

    public ForgeItemVisualService(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.transport = (player, packet) -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        this.packetFactory = new PacketEventsPacketFactory();
        this.entityIds = () -> {
            try {
                return io.github.retrooper.packetevents.util.SpigotReflectionUtil.generateEntityId();
            } catch (RuntimeException | LinkageError e) {
                throw new RuntimeException("SpigotReflectionUtil unavailable", e);
            }
        };
    }

    ForgeItemVisualService(Logger logger, PacketTransport transport, PacketFactory packetFactory, IntSupplier entityIds) {
        this.logger = logger;
        this.transport = transport;
        this.packetFactory = packetFactory;
        this.entityIds = entityIds;
    }

    public boolean spawn(String transactionId, Player viewer, org.bukkit.inventory.ItemStack bukkitItem, org.bukkit.Location bukkitLocation) {
        if (transactionId == null || viewer == null || bukkitItem == null || bukkitLocation == null) {
            return false;
        }
        if (bukkitItem.getType() == org.bukkit.Material.AIR || bukkitItem.getAmount() <= 0) {
            return false;
        }

        destroy(transactionId);

        Integer entityId = null;
        boolean spawnSent = false;
        try {
            entityId = entityIds.getAsInt();
            UUID entityUuid = UUID.randomUUID();
            SpawnPacketBundle bundle = packetFactory.createSpawnPackets(entityId, entityUuid, bukkitLocation, bukkitItem);
            if (bundle == null) {
                throw new IllegalStateException("Spawn packet bundle was null");
            }
            Object metadataPacket = bundle.getMetadataPacket();
            transport.send(viewer, bundle.getSpawnPacket());
            spawnSent = true;
            transport.send(viewer, metadataPacket);
            active.put(transactionId, new FakeItemState(entityId, entityUuid, viewer, metadataPacket));
            return true;
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING,
                "Failed to create or send item visual for transaction " + transactionId, e);
            if (spawnSent && entityId != null) {
                destroyUntracked(viewer, entityId, transactionId);
            }
            return false;
        }
    }

    public boolean move(String transactionId, org.bukkit.Location bukkitLocation) {
        if (bukkitLocation == null) {
            return false;
        }
        FakeItemState state = active.get(transactionId);
        if (state == null) {
            return false;
        }

        try {
            transport.send(state.viewer, packetFactory.createTeleportPacket(state.entityId, bukkitLocation));
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING,
                "Failed to send EntityTeleport for transaction " + transactionId, e);
            destroy(transactionId);
            return false;
        }
        return true;
    }

    public boolean refreshMetadata(String transactionId) {
        FakeItemState state = active.get(transactionId);
        if (state == null) {
            return false;
        }
        try {
            transport.send(state.viewer, state.metadataPacket);
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING,
                "Failed to refresh EntityMetadata for transaction " + transactionId, e);
            destroy(transactionId);
            return false;
        }
        return true;
    }

    private void destroyUntracked(Player viewer, int entityId, String transactionId) {
        try {
            transport.send(viewer, packetFactory.createDestroyPacket(entityId));
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING,
                "Failed to compensate item visual for transaction " + transactionId, e);
        }
    }

    public void destroy(String transactionId) {
        FakeItemState state = active.remove(transactionId);
        if (state == null) {
            return;
        }
        try {
            transport.send(state.viewer, packetFactory.createDestroyPacket(state.entityId));
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING,
                "Failed to send DestroyEntities for transaction " + transactionId, e);
        }
    }

    public void destroyAll() {
        for (String transactionId : active.keySet().toArray(new String[0])) {
            destroy(transactionId);
        }
        active.clear();
    }

    static int itemStackMetadataIndex(ClientVersion version) {
        Objects.requireNonNull(version, "version");
        if (version.isOlderThan(ClientVersion.V_1_9)) return 10;
        if (version.isOlderThan(ClientVersion.V_1_14)) return 6;
        if (version.isOlderThan(ClientVersion.V_1_17)) return 7;
        return 8;
    }

    private static final class PacketEventsPacketFactory implements PacketFactory {
        @Override
        public SpawnPacketBundle createSpawnPackets(int entityId, UUID entityUuid, org.bukkit.Location bukkitLocation, org.bukkit.inventory.ItemStack bukkitItem) {
            Location location = io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitLocation(bukkitLocation);
            ItemStack item = io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack(bukkitItem);

            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                entityUuid,
                EntityTypes.ITEM,
                location,
                (float) 0,
                0,
                new Vector3d(0, 0, 0)
            );

            EntityMetadataProvider metadataProvider = version -> {
                int index = itemStackMetadataIndex(version);
                EntityData<ItemStack> data = new EntityData<>(index, EntityDataTypes.ITEMSTACK, item);
                return Collections.singletonList(data);
            };

            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadataProvider);
            return new SpawnPacketBundle(spawnPacket, metadataPacket);
        }

        @Override
        public Object createTeleportPacket(int entityId, org.bukkit.Location bukkitLocation) {
            Location location = io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitLocation(bukkitLocation);
            return new WrapperPlayServerEntityTeleport(entityId, location, true);
        }

        @Override
        public Object createDestroyPacket(int entityId) {
            return new WrapperPlayServerDestroyEntities(entityId);
        }
    }

    private final class FakeItemState {
        final int entityId;
        final UUID entityUuid;
        final Player viewer;
        final Object metadataPacket;

        FakeItemState(int entityId, UUID entityUuid, Player viewer, Object metadataPacket) {
            this.entityId = entityId;
            this.entityUuid = entityUuid;
            this.viewer = viewer;
            this.metadataPacket = metadataPacket;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FakeItemState that = (FakeItemState) o;
            return entityId == that.entityId &&
                Objects.equals(entityUuid, that.entityUuid) &&
                Objects.equals(viewer, that.viewer) &&
                Objects.equals(metadataPacket, that.metadataPacket);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityId, entityUuid, viewer, metadataPacket);
        }

        @Override
        public String toString() {
            return "FakeItemState{entityId=" + entityId + ", entityUuid=" + entityUuid + ", viewer=" + viewer + "}";
        }
    }
}
