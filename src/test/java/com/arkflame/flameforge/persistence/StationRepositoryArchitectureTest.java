package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.testfakes.TaskHandleStub;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StationRepositoryArchitectureTest {

    private StationRepository repository;
    private JavaPlugin fakePlugin;
    private FakeSchedulerBridge scheduler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        fakePlugin = pluginWithLogger();
        scheduler = new FakeSchedulerBridge();
        repository = new StationRepository(fakePlugin, scheduler, tempDir);
    }

    @Test
    void emptyLoadAndSecondSaveProduceAtomicReplaceableFile() throws Exception {
        repository.load();
        assertEquals(0, repository.size());
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-one", UUID.randomUUID(), "world", 10, 64, 20, "default")));
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-two", UUID.randomUUID(), "world", 30, 64, 40, "default")));
        assertEquals(2, repository.size());
    }

    @Test
    void addRejectsDuplicateIdAndLocationAndReturnsAddedForge() throws Exception {
        Files.createDirectories(tempDir);
        StationRepository.RegisteredForge forge = new StationRepository.RegisteredForge(
            "my-forge", UUID.randomUUID(), "world", 8, 64, 8, "default");
        await(repository.addAndSave(forge));

        StationRepository.AddOutcome duplicateIdOutcome = await(repository.addAndSave(
            new StationRepository.RegisteredForge("my-forge", UUID.randomUUID(), "world", 9, 64, 9, "default")));
        assertEquals(StationRepository.AddResult.DUPLICATE_ID, duplicateIdOutcome.getResult());

        StationRepository.AddOutcome duplicateLocOutcome = await(repository.addAndSave(
            new StationRepository.RegisteredForge("another-forge", UUID.randomUUID(), "world", 8, 64, 8, "default")));
        assertEquals(StationRepository.AddResult.DUPLICATE_LOCATION, duplicateLocOutcome.getResult());

        StationRepository.AddOutcome addedOutcome = await(repository.addAndSave(
            new StationRepository.RegisteredForge("new-forge", UUID.randomUUID(), "world", 11, 64, 11, "default")));
        assertEquals(StationRepository.AddResult.ADDED, addedOutcome.getResult());
        assertNotNull(addedOutcome.getAddedForge());
        assertEquals("new-forge", addedOutcome.getAddedForge().getId());
    }

    @Test
    void removeAndFindByIdAndKeyMaintainConsistentIndexes() throws Exception {
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "toremove", UUID.randomUUID(), "world", 7, 64, 7, "default")));
        assertTrue(repository.findById("toremove").isPresent());
        assertTrue(repository.findByKey("world", 7, 64, 7).isPresent());

        StationRepository.RemoveOutcome outcome = await(repository.removeAndSave("toremove"));
        assertEquals(StationRepository.Result.REMOVED, outcome.getResult());
        assertFalse(repository.findById("toremove").isPresent());
        assertFalse(repository.findByKey("world", 7, 64, 7).isPresent());
    }

    @Test
    void snapshotIsSortedByIdAndSizeMatches() throws Exception {
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-z", UUID.randomUUID(), "world", 3, 64, 3, "default")));
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-a", UUID.randomUUID(), "world", 1, 64, 1, "default")));
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-m", UUID.randomUUID(), "world", 2, 64, 2, "default")));
        List<StationRepository.RegisteredForge> snapshot = repository.snapshotSortedById();
        assertEquals(3, snapshot.size());
        assertEquals("forge-a", snapshot.get(0).getId());
        assertEquals("forge-m", snapshot.get(1).getId());
        assertEquals("forge-z", snapshot.get(2).getId());
        assertEquals(3, repository.size());
    }

    @Test
    void stationKeyUsesWorldUuidAndCoordinates() {
        UUID worldUuid = UUID.randomUUID();
        StationRepository.StationKey key1 = new StationRepository.StationKey(
            worldUuid, "world1", 10, 64, 20);
        StationRepository.StationKey key2 = new StationRepository.StationKey(
            worldUuid, "world2", 10, 64, 20);
        assertEquals(key1, key2);
    }

    @Test
    void loadAsyncReadsWorldIdentityWithoutBukkitWorldLookup() throws Exception {
        UUID worldUuid = UUID.randomUUID();
        Files.write(tempDir.resolve("stations.yml"), java.util.Arrays.asList(
            "alpha:", "  id: Alpha", "  world: unloaded-world", "  worldUuid: " + worldUuid,
            "  x: 1", "  y: 64", "  z: 2", "  profile: default"));
        ControlledSchedulerBridge controlled = new ControlledSchedulerBridge();
        JavaPlugin plugin = pluginWithLogger();
        StationRepository asyncRepository = new StationRepository(plugin, controlled, tempDir);

        CompletableFuture<Void> load = asyncRepository.loadAsync();
        assertFalse(load.isDone());
        controlled.runNext();
        await(load);

        StationRepository.RegisteredForge forge = asyncRepository.findById("alpha").orElseThrow(AssertionError::new);
        assertEquals(worldUuid, forge.getWorldUuid());
        assertEquals("unloaded-world", forge.getWorldName());
        verifyNoInteractions(plugin);
    }

    @Test
    void loadAsyncPublishesIdAndLocationMapsTogether() throws Exception {
        Files.write(tempDir.resolve("stations.yml"), java.util.Arrays.asList(
            "first:", "  id: First", "  world: world", "  x: 1", "  y: 64", "  z: 2",
            "second:", "  id: Second", "  world: world", "  x: 1", "  y: 64", "  z: 2"));
        ControlledSchedulerBridge controlled = new ControlledSchedulerBridge();
        StationRepository asyncRepository = new StationRepository(pluginWithLogger(), controlled, tempDir);

        CompletableFuture<Void> load = asyncRepository.loadAsync();
        controlled.runNext();
        await(load);

        assertEquals(1, asyncRepository.size());
        assertTrue(asyncRepository.findById("first").isPresent());
        assertEquals("first", asyncRepository.findByKey("world", 1, 64, 2).orElseThrow(AssertionError::new).getId());
        assertFalse(asyncRepository.findById("second").isPresent());
    }

    private static JavaPlugin pluginWithLogger() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(StationRepositoryArchitectureTest.class.getName()));
        return plugin;
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(1, TimeUnit.SECONDS);
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
}
