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
import java.util.ArrayList;
import java.util.Arrays;
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
    void stationCrudUsesIndependentFilesAndStableSnapshot() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID worldUuid = UUID.randomUUID();

        StationRepository.AddOutcome outcomeA = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-a", worldUuid, "world", 10, 64, 20, "default")));
        assertEquals(StationRepository.AddResult.ADDED, outcomeA.getResult());

        StationRepository.AddOutcome outcomeB = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-b", worldUuid, "world", 30, 64, 40, "default")));
        assertEquals(StationRepository.AddResult.ADDED, outcomeB.getResult());

        assertTrue(Files.exists(tempDir.resolve("stations/forge-a.yml")));
        assertTrue(Files.exists(tempDir.resolve("stations/forge-b.yml")));

        List<StationRepository.RegisteredForge> published = repository.snapshotSortedById();
        assertEquals(Arrays.asList("forge-a", "forge-b"), ids(published));
        assertThrows(UnsupportedOperationException.class, () -> published.clear());

        StationRepository.RemoveOutcome removeOutcome = await(repository.removeAndSave("forge-a"));
        assertEquals(StationRepository.Result.REMOVED, removeOutcome.getResult());
        assertEquals(Arrays.asList("forge-b"), repository.snapshotIds());
        assertEquals(Arrays.asList("forge-a", "forge-b"), ids(published));
        assertFalse(repository.findById("forge-a").isPresent());
        assertTrue(repository.findById("forge-b").isPresent());
    }

    @Test
    void corruptOrDuplicateFileDoesNotPoisonValidStations() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID worldUuid = UUID.randomUUID();

        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "valid-station", worldUuid, "world", 10, 64, 20, "default")));
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "corrupt-station", UUID.randomUUID(), "other-world", 30, 64, 40, "default")));
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "other-valid", UUID.randomUUID(), "other-world", 50, 64, 60, "default")));
        Files.write(tempDir.resolve("stations/duplicate-station.yml"), java.util.Arrays.asList(
            stationYaml(UUID.randomUUID(), "world", 10, 64, 20, "default").split("\n")));

        Files.write(tempDir.resolve("stations/corrupt-station.yml"), java.util.Arrays.asList(
            "this-is: [invalid", "yaml: that"));
        ControlledSchedulerBridge controlled = new ControlledSchedulerBridge();
        StationRepository freshRepo = new StationRepository(pluginWithLogger(), controlled, tempDir);
        CompletableFuture<StationRepository.StationLoadReport> loadFuture = freshRepo.loadAsync();
        controlled.runNext();
        StationRepository.StationLoadReport report = await(loadFuture);

        assertEquals(2, report.getLoadedCount());
        assertEquals(2, report.getSkippedCount());
        assertTrue(report.getIssues().stream().anyMatch(issue ->
            issue.getType() == StationRepository.StationFileIssueType.MALFORMED_YAML));
        assertTrue(report.getIssues().stream().anyMatch(issue ->
            issue.getType() == StationRepository.StationFileIssueType.DUPLICATE_LOCATION));

        assertTrue(freshRepo.findById("duplicate-station").isPresent());
        assertFalse(freshRepo.findById("valid-station").isPresent());
        assertTrue(freshRepo.findById("other-valid").isPresent());
        assertFalse(freshRepo.findById("corrupt-station").isPresent());
    }

    @Test
    void writeFailurePreservesPreviouslyPublishedState() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID stableUuid = UUID.randomUUID();

        StationRepository.AddOutcome stableOutcome = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "stable", stableUuid, "world", 10, 64, 20, "default")));
        assertEquals(StationRepository.AddResult.ADDED, stableOutcome.getResult());

        Files.createDirectory(tempDir.resolve("stations/.new-station.yml.tmp"));

        StationRepository.AddOutcome failOutcome = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "new-station", UUID.randomUUID(), "world", 30, 64, 40, "default")));
        assertEquals(StationRepository.AddResult.PERSISTENCE_FAILED, failOutcome.getResult());
        assertNotNull(failOutcome.getReference());

        assertEquals(1, repository.size());
        assertTrue(repository.findById("stable").isPresent());
        assertFalse(repository.findById("new-station").isPresent());
    }

    private static List<String> ids(List<StationRepository.RegisteredForge> stations) {
        List<String> ids = new ArrayList<>();
        for (StationRepository.RegisteredForge station : stations) {
            ids.add(station.getId());
        }
        return ids;
    }

    private static String stationYaml(UUID worldUuid, String world, int x, int y, int z, String profile) {
        return "schema-version: 1\nworld:\n  name: " + world + "\n  uuid: \"" + worldUuid
            + "\"\nlocation:\n  x: " + x + "\n  y: " + y + "\n  z: " + z + "\nprofile: " + profile + "\n";
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
