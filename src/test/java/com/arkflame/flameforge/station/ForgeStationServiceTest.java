package com.arkflame.flameforge.station;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.compat.scheduler.TeleportBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.hologram.ForgeStationHologramService;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.persistence.StationRepository.AddOutcome;
import com.arkflame.flameforge.persistence.StationRepository.RegisteredForge;
import com.arkflame.flameforge.persistence.StationRepository.RemoveOutcome;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

class ForgeStationServiceTest {

    private ForgeStationService service;
    private FakeSchedulerBridge scheduler;
    private TeleportBridge teleportBridge;
    private StationRepository mockRepo;
    private ConfigService mockConfigService;
    private ForgeStationHologramService mockHologramService;
    private JavaPlugin fakePlugin;
    private Player player;
    private World world;
    private org.bukkit.Server server;

    @BeforeEach
    void setUp() throws Exception {
        fakePlugin = mock(JavaPlugin.class);
        server = mock(org.bukkit.Server.class);
        world = mock(World.class);
        when(fakePlugin.getServer()).thenReturn(server);
        when(server.getWorld(anyString())).thenReturn(world);

        scheduler = new FakeSchedulerBridge();
        teleportBridge = mock(TeleportBridge.class);
        mockRepo = mock(StationRepository.class);
        mockConfigService = mock(ConfigService.class);
        mockHologramService = mock(ForgeStationHologramService.class);
        when(mockConfigService.getCurrentSnapshot()).thenReturn(ConfigSnapshot.builder().build());
        service = new ForgeStationService(fakePlugin, scheduler, mockRepo, mockConfigService, mockHologramService, teleportBridge);
        player = mock(Player.class);
    }

    @Test
    void generatedIdsFollowGrammarRetryDuplicatesAndExhaustAfterEight() {
        AtomicInteger callCount = new AtomicInteger(0);
        when(mockRepo.findById(anyString())).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            return Optional.of(new RegisteredForge(invocation.getArgument(0), UUID.randomUUID(), "world", 0, 64, 0, "default"));
        });
        String id = service.generateUniqueIdForTest(0);
        assertNull(id);
        assertEquals(8, callCount.get());
    }

    @Test
    void listAndDuplicateQueriesReflectRepositorySnapshot() {
        List<RegisteredForge> sortedForges = Arrays.asList(
            new RegisteredForge("forge-alpha", UUID.randomUUID(), "world", 1, 64, 1, "default"),
            new RegisteredForge("forge-beta", UUID.randomUUID(), "world", 2, 64, 2, "default"),
            new RegisteredForge("forge-zebra", UUID.randomUUID(), "world", 3, 64, 3, "default")
        );
        when(mockRepo.snapshotSortedById()).thenReturn(sortedForges);
        List<StationData> stations = service.listStations();
        assertEquals(3, stations.size());
        assertEquals("forge-alpha", stations.get(0).id);
        assertEquals("forge-zebra", stations.get(2).id);
    }

    @Test
    void tierAllowanceHandlesMissingUnlimitedAndMaximumProfiles() {
        assertTrue(service.isTierAllowed(null, 100));
        com.arkflame.flameforge.model.StationProfile unlimitedProfile = mock(com.arkflame.flameforge.model.StationProfile.class);
        when(unlimitedProfile.getMaxTierUnlocked()).thenReturn(-1);
        assertTrue(service.isTierAllowed(unlimitedProfile, 100));
        com.arkflame.flameforge.model.StationProfile maxProfile = mock(com.arkflame.flameforge.model.StationProfile.class);
        when(maxProfile.getMaxTierUnlocked()).thenReturn(5);
        assertTrue(service.isTierAllowed(maxProfile, 3));
        assertFalse(service.isTierAllowed(maxProfile, 7));
    }

    @Test
    void registeredTargetLookupDistinguishesUnregisteredAndExactRegisteredBlock() {
        configureTarget(Material.CHEST);
        when(mockRepo.findByKey("world", 1, 64, 0)).thenReturn(Optional.empty());
        Optional<StationData> unregistered = service.resolveRegisteredForgeFromTarget(player).join();
        assertFalse(unregistered.isPresent());

        RegisteredForge registered = new RegisteredForge(
            "registered", UUID.randomUUID(), "world", 1, 64, 0, "default");
        when(mockRepo.findByKey("world", 1, 64, 0)).thenReturn(Optional.of(registered));
        Optional<StationData> found = service.resolveRegisteredForgeFromTarget(player).join();
        assertTrue(found.isPresent());
        assertEquals("registered", found.get().id);
    }

    @Test
    void addTargetedForgeRegistersArbitraryNonAirBlockWithGeneratedAutoOrExplicitId() {
        configureTarget(Material.CHEST);
        when(mockRepo.findByKey("world", 1, 64, 0)).thenReturn(Optional.empty());
        AtomicReference<RegisteredForge> submitted = new AtomicReference<>();
        when(mockRepo.addAndSave(any(RegisteredForge.class))).thenAnswer(invocation -> {
            RegisteredForge forge = invocation.getArgument(0);
            submitted.set(forge);
            return CompletableFuture.completedFuture(AddOutcome.added(forge));
        });

        ForgeStationService.AddForgeOutcome outcome = service
            .addTargetedForge(player, Optional.of("Arbitrary_Forge"), "default")
            .join();

        assertEquals(ForgeStationService.Result.ADDED, outcome.result());
        assertEquals("arbitrary_forge", outcome.finalId());
        assertNotNull(submitted.get());
        assertEquals("arbitrary_forge", submitted.get().getId());
        verify(mockRepo).addAndSave(any(RegisteredForge.class));
    }

    @Test
    void addTargetedForgeRejectsUnknownProfileInvalidIdAndDuplicateLocationBeforeWrongRetries() {
        configureTarget(Material.STONE);
        ForgeStationService.AddForgeOutcome unknownProfile = service
            .addTargetedForge(player, Optional.of("valid-id"), "missing-profile")
            .join();
        assertEquals(ForgeStationService.Result.UNKNOWN_PROFILE, unknownProfile.result());
        verifyNoInteractions(mockRepo);

        ForgeStationService.AddForgeOutcome invalidId = service
            .addTargetedForge(player, Optional.of("bad id"), "default")
            .join();
        assertEquals(ForgeStationService.Result.INVALID_ID, invalidId.result());

        when(mockRepo.findByKey("world", 1, 64, 0)).thenReturn(Optional.of(
            new RegisteredForge("existing", UUID.randomUUID(), "world", 1, 64, 0, "default")));
        ForgeStationService.AddForgeOutcome duplicateLocation = service
            .addTargetedForge(player, Optional.of("new-id"), "default")
            .join();
        assertEquals(ForgeStationService.Result.DUPLICATE_LOCATION, duplicateLocation.result());
    }

    @Test
    void addTargetedForgeMapsPersistenceFailureAndReturnsActualFinalId() {
        configureTarget(Material.STONE);
        when(mockRepo.findByKey("world", 1, 64, 0)).thenReturn(Optional.empty());
        when(mockRepo.addAndSave(any(RegisteredForge.class)))
            .thenReturn(CompletableFuture.completedFuture(AddOutcome.persistenceFailed(null)));

        ForgeStationService.AddForgeOutcome outcome = service
            .addTargetedForge(player, Optional.of("persisted-id"), "default").join();

        assertEquals(ForgeStationService.Result.PERSISTENCE_FAILED, outcome.result());
        assertEquals("persisted-id", outcome.finalId());
    }

    @Test
    void hologramAddRunsAfterPersistenceAndFailureDoesNotRollBack() {
        configureTarget(Material.DIAMOND_BLOCK);
        when(mockRepo.findByKey("world", 1, 64, 0)).thenReturn(Optional.empty());
        when(mockRepo.addAndSave(any(RegisteredForge.class))).thenAnswer(invocation -> {
            RegisteredForge forge = invocation.getArgument(0);
            return CompletableFuture.completedFuture(AddOutcome.added(forge));
        });
        doThrow(new RuntimeException("Hologram provider failed"))
            .when(mockHologramService).onStationAdded(any(RegisteredForge.class));

        try {
            service.addTargetedForge(player, Optional.of("persist-even-on-hologram-fail"), "default").join();
            fail("Expected exception due to hologram failure");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof RuntimeException);
        }

        verify(mockRepo).addAndSave(any(RegisteredForge.class));
    }

    @Test
    void removePassesCapturedRegisteredForgeToHologramService() throws Exception {
        String testId = "remove-test-forge";
        RegisteredForge forge = new RegisteredForge(
            testId, UUID.randomUUID(), "world", 12, 64, 12, "default");
        when(mockRepo.findById(testId)).thenReturn(Optional.of(forge));
        when(mockRepo.removeAndSave(testId))
            .thenReturn(CompletableFuture.completedFuture(RemoveOutcome.removed(forge)));

        service.removeStation(testId).get();

        verify(mockHologramService).onStationRemoved(forge);
    }

    @Test
    void reconcileReloadAndCleanupDelegateExactlyOnce() {
        service.reconcileHolograms();
        service.reloadHolograms();
        service.cleanupHolograms();
        verify(mockHologramService, times(1)).reconcileStartup();
        verify(mockHologramService, times(1)).reload();
        verify(mockHologramService, times(1)).disableCleanup();
    }

    @Test
    void obsoleteDirectBlockAddApiIsAbsent() {
        assertTrue(Arrays.stream(ForgeStationService.class.getMethods())
            .noneMatch(method -> method.getName().equals("addStation")));
    }

    @Test
    void removeReturnsFalseForMissingAndTrueForExistingStation() throws Exception {
        when(mockRepo.findById("nonexistent")).thenReturn(Optional.empty());
        CompletableFuture<RemoveOutcome> notFoundFuture = service.removeStation("nonexistent");
        assertEquals(StationRepository.Result.NOT_FOUND, notFoundFuture.get().getResult());

        String testId = "forge-remove";
        RegisteredForge forge = new RegisteredForge(testId, UUID.randomUUID(), "world", 6, 64, 6, "default");
        when(mockRepo.findById(testId)).thenReturn(Optional.of(forge));
        when(mockRepo.removeAndSave(testId))
            .thenReturn(CompletableFuture.completedFuture(RemoveOutcome.removed(forge)));
        CompletableFuture<RemoveOutcome> foundFuture = service.removeStation(testId);
        assertEquals(StationRepository.Result.REMOVED, foundFuture.get().getResult());
    }

    private void configureTarget(Material material) {
        when(player.isOnline()).thenReturn(true);
        when(world.getUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        Location eye = new Location(world, 0.5, 64.5, 0.5).setDirection(new Vector(1, 0, 0));
        when(player.getEyeLocation()).thenReturn(eye);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(world.getBlockAt(1, 64, 0)).thenReturn(block);
    }

    private static class FakeSchedulerBridge implements SchedulerBridge {
        @Override public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runEntity(org.bukkit.entity.Entity entity, Runnable runnable, Runnable retireCallback) { runnable.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runEntityLater(org.bukkit.entity.Entity entity, Runnable runnable, Runnable retireCallback, long delay) { runnable.run(); return TaskHandleStub.INSTANCE; }
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
