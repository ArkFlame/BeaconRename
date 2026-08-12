package com.arkflame.flameforge.station;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.compat.scheduler.TeleportBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.hologram.ForgeStationHologramService;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.persistence.StationRepository.AddOutcome;
import com.arkflame.flameforge.persistence.StationRepository.RegisteredForge;
import com.arkflame.flameforge.persistence.StationRepository.StationData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeStationServiceTest {

    private ForgeStationService service;
    private FakeScheduler scheduler;
    private StationRepository repository;
    private ConfigService configService;
    private ForgeStationHologramService holograms;
    private Player player;
    private World world;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        world = mock(World.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorld(anyString())).thenReturn(world);
        scheduler = new FakeScheduler();
        repository = mock(StationRepository.class);
        configService = mock(ConfigService.class);
        when(configService.getCurrentSnapshot()).thenReturn(ConfigSnapshot.builder().build());
        holograms = mock(ForgeStationHologramService.class);
        service = new ForgeStationService(plugin, scheduler, repository, configService, holograms,
            mock(TeleportBridge.class));
        player = mock(Player.class);
    }

    @Test
    void stationAddLookupListAndRemoveFlowPersistsThroughRepository() {
        configureTarget(Material.CHEST);
        when(repository.findByKey("world", 1, 64, 0)).thenReturn(Optional.empty());
        RegisteredForge forge = new RegisteredForge("forge-alpha", UUID.randomUUID(), "world", 1, 64, 0, "default");
        when(repository.addAndSave(any(RegisteredForge.class)))
            .thenReturn(CompletableFuture.completedFuture(AddOutcome.added(forge)));

        ForgeStationService.AddForgeOutcome added = service
            .addTargetedForge(player, Optional.of("Forge_Alpha"), "default").join();
        assertEquals(ForgeStationService.Result.SUCCESS, added.result());
        assertEquals("forge-alpha", added.finalId());

        when(repository.findById("forge-alpha")).thenReturn(Optional.of(forge));
        when(repository.snapshotSortedById()).thenReturn(Collections.singletonList(forge));
        Optional<StationData> lookup = service.getStationById("forge-alpha");
        List<StationData> listed = service.listStations();
        assertTrue(lookup.isPresent());
        assertEquals("forge-alpha", lookup.get().id);
        assertEquals(1, listed.size());

        when(repository.removeAndSave("forge-alpha"))
            .thenReturn(CompletableFuture.completedFuture(StationRepository.RemoveOutcome.removed(forge)));
        StationRepository.RemoveOutcome removed = service.removeStation("forge-alpha").join();
        assertEquals(StationRepository.Result.REMOVED, removed.getResult());
        verify(repository).addAndSave(any(RegisteredForge.class));
        verify(repository).removeAndSave("forge-alpha");
        verify(holograms).onStationAdded(forge);
        verify(holograms).onStationRemoved(forge);
    }

    @Test
    void tierAllowanceAndDuplicateValidationRejectInvalidStationState() {
        assertTrue(service.isTierAllowed(null, 20));
        assertTrue(service.isTierAllowed(StationProfile.of("unlimited", "", -1, Collections.emptyList()), 20));
        assertFalse(service.isTierAllowed(StationProfile.of("limited", "", 3, Collections.emptyList()), 4));

        RegisteredForge existing = new RegisteredForge("existing", UUID.randomUUID(), "world", 1, 64, 0, "default");
        when(repository.findById("existing")).thenReturn(Optional.of(existing));
        when(repository.findByKey("world", 1, 64, 0)).thenReturn(Optional.of(existing));
        assertTrue(service.isDuplicateId("existing"));
        assertTrue(service.isDuplicateId(null));
        assertTrue(service.isDuplicateCoordinate("world", 1.9, 64.2, 0.8));
        assertFalse(service.resolveStationAt(null, 1, 64, 0).isPresent());
        assertFalse(service.resolveStationAt((Block) null).isPresent());

        configureTarget(Material.CHEST);
        when(repository.findByKey("world", 1, 64, 0)).thenReturn(Optional.empty());
        ForgeStationService.AddForgeOutcome invalid = service
            .addTargetedForge(player, Optional.of("bad id"), "default").join();
        assertEquals(ForgeStationService.Result.INVALID_ID, invalid.result());
    }

    private void configureTarget(Material material) {
        when(player.isOnline()).thenReturn(true);
        when(world.getUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(player.getEyeLocation()).thenReturn(new Location(world, 0.5, 64.5, 0.5)
            .setDirection(new Vector(1, 0, 0)));
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(world.getBlockAt(1, 64, 0)).thenReturn(block);
    }

    private static class FakeScheduler implements SchedulerBridge {
        @Override public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runEntity(org.bukkit.entity.Entity entity, Runnable task, Runnable retire) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runEntityLater(org.bukkit.entity.Entity entity, Runnable task, Runnable retire, long delay) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runRegion(Location location, Runnable task) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runRegionLater(Location location, Runnable task, long delay) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runAsync(JavaPlugin plugin, Runnable task) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public void cancelAll(JavaPlugin plugin) {}
        @Override public boolean isFolia() { return false; }
    }

    private enum TaskHandleStub implements TaskHandle {
        INSTANCE;

        @Override public void cancel() {}
        @Override public boolean isCancelled() { return false; }
    }
}
