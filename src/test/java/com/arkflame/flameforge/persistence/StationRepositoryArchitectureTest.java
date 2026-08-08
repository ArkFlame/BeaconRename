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
    void savesEachStationAsIndependentCanonicalFileAndRejectsDuplicateId() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID worldUuid = UUID.randomUUID();

        StationRepository.AddOutcome outcomeA = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-a", worldUuid, "world", 10, 64, 20, "default")));
        assertEquals(StationRepository.AddResult.ADDED, outcomeA.getResult());

        StationRepository.AddOutcome outcomeB = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-b", worldUuid, "world", 30, 64, 40, "default")));
        assertEquals(StationRepository.AddResult.ADDED, outcomeB.getResult());

        StationRepository.AddOutcome duplicateOutcome = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "forge-a", UUID.randomUUID(), "world", 50, 64, 60, "default")));
        assertEquals(StationRepository.AddResult.DUPLICATE_ID, duplicateOutcome.getResult());

        assertTrue(Files.exists(tempDir.resolve("stations/forge-a.yml")));
        assertTrue(Files.exists(tempDir.resolve("stations/forge-b.yml")));
        assertFalse(Files.exists(tempDir.resolve("stations.yml")));

        assertEquals(2, repository.size());
        assertTrue(repository.findById("forge-a").isPresent());
        assertTrue(repository.findById("forge-b").isPresent());

        org.bukkit.configuration.file.YamlConfiguration config = 
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                tempDir.resolve("stations/forge-a.yml").toFile());
        assertFalse(config.contains("id"));
        assertEquals(1, config.getInt("schema-version"));
        assertEquals(Arrays.asList("schema-version", "world", "location", "profile"), new java.util.ArrayList<>(config.getKeys(false)));
    }

    @Test
    void corruptingOneStationFileSkipsOnlyThatStation() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID worldUuid = UUID.randomUUID();

        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "valid-station", worldUuid, "world", 10, 64, 20, "default")));
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "corrupt-station", UUID.randomUUID(), "other-world", 30, 64, 40, "default")));

        Files.write(tempDir.resolve("stations/corrupt-station.yml"), java.util.Arrays.asList(
            "this-is: [invalid", "yaml: that"));
        ControlledSchedulerBridge controlled = new ControlledSchedulerBridge();
        StationRepository freshRepo = new StationRepository(pluginWithLogger(), controlled, tempDir);
        CompletableFuture<StationRepository.StationLoadReport> loadFuture = freshRepo.loadAsync();
        controlled.runNext();
        StationRepository.StationLoadReport report = await(loadFuture);

        assertEquals(1, report.getLoadedCount());
        assertEquals(1, report.getSkippedCount());

        StationRepository.StationFileIssue issue = report.getIssues().get(0);
        assertEquals(StationRepository.StationFileIssueType.MALFORMED_YAML, issue.getType());
        assertEquals("corrupt-station.yml", issue.getRelativePath());
        assertNotNull(issue.getReference());

        assertTrue(freshRepo.findById("valid-station").isPresent());
        assertFalse(freshRepo.findById("corrupt-station").isPresent());

        StationRepository.RegisteredForge valid = freshRepo.findById("valid-station").orElseThrow(() -> new java.util.NoSuchElementException("valid-station not found"));
        assertEquals(worldUuid, valid.getWorldUuid());
    }

    @Test
    void removeDeletesOnlyTargetFileAndPreservesSiblingBytes() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID worldUuid = UUID.randomUUID();

        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "target", worldUuid, "world", 10, 64, 20, "default")));
        await(repository.addAndSave(new StationRepository.RegisteredForge(
            "sibling", UUID.randomUUID(), "world", 30, 64, 40, "default")));

        byte[] siblingBytesBefore = Files.readAllBytes(tempDir.resolve("stations/sibling.yml"));

        StationRepository.RemoveOutcome removeOutcome = await(repository.removeAndSave("target"));
        assertEquals(StationRepository.Result.REMOVED, removeOutcome.getResult());

        assertFalse(Files.exists(tempDir.resolve("stations/target.yml")));
        byte[] siblingBytesAfter = Files.readAllBytes(tempDir.resolve("stations/sibling.yml"));
        assertArrayEquals(siblingBytesBefore, siblingBytesAfter);

        assertFalse(repository.findById("target").isPresent());
        assertTrue(repository.findById("sibling").isPresent());
    }

    @Test
    void duplicateLocationUsesSortedFilenameWinnerAndReportsSkippedSibling() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID worldUuid = UUID.randomUUID();

        String stationA = "schema-version: 1\nworld:\n  name: world\n  uuid: \"" + worldUuid + "\"\nlocation:\n  x: 10\n  y: 64\n  z: 20\nprofile: default\n";
        String stationB = "schema-version: 1\nworld:\n  name: world\n  uuid: \"" + UUID.randomUUID() + "\"\nlocation:\n  x: 10\n  y: 64\n  z: 20\nprofile: default\n";

        Files.write(tempDir.resolve("stations/a.yml"), java.util.Arrays.asList(stationA.split("\n")));
        Files.write(tempDir.resolve("stations/b.yml"), java.util.Arrays.asList(stationB.split("\n")));

        ControlledSchedulerBridge controlled = new ControlledSchedulerBridge();
        StationRepository repo = new StationRepository(pluginWithLogger(), controlled, tempDir);
        CompletableFuture<StationRepository.StationLoadReport> loadFuture = repo.loadAsync();
        controlled.runNext();
        StationRepository.StationLoadReport report = await(loadFuture);

        assertEquals(1, report.getLoadedCount());
        assertEquals(1, report.getSkippedCount());

        StationRepository.StationFileIssue issue = report.getIssues().get(0);
        assertEquals(StationRepository.StationFileIssueType.DUPLICATE_LOCATION, issue.getType());
        assertEquals("b.yml", issue.getRelativePath());

        assertTrue(repo.findById("a").isPresent());
        assertFalse(repo.findById("b").isPresent());
    }

    @Test
    void legacyMonolithicFileIsIgnoredWithoutMigration() throws Exception {
        String legacyContent = "forge-legacy:\n  world: world\n  x: 1\n  y: 64\n  z: 2\n  profile: default\n";
        Files.write(tempDir.resolve("stations.yml"), java.util.Arrays.asList(legacyContent.split("\n")));

        ControlledSchedulerBridge controlled = new ControlledSchedulerBridge();
        StationRepository repo = new StationRepository(pluginWithLogger(), controlled, tempDir);
        CompletableFuture<StationRepository.StationLoadReport> loadFuture = repo.loadAsync();
        controlled.runNext();
        StationRepository.StationLoadReport report = await(loadFuture);

        assertTrue(Files.exists(tempDir.resolve("stations")));
        assertEquals(0, report.getLoadedCount());

        assertFalse(repo.findById("forge-legacy").isPresent());

        byte[] legacyBytesAfter = Files.readAllBytes(tempDir.resolve("stations.yml"));
        assertEquals(legacyContent, new String(legacyBytesAfter));

        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir.resolve("stations"), "*.yml")) {
            int perStationFiles = 0;
            for (Path p : stream) {
                perStationFiles++;
            }
            assertEquals(0, perStationFiles);
        }
    }

    @Test
    void writeFailureLeavesPublishedIndexAndExistingFilesUnchanged() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID stableUuid = UUID.randomUUID();

        StationRepository.AddOutcome stableOutcome = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "stable", stableUuid, "world", 10, 64, 20, "default")));
        assertEquals(StationRepository.AddResult.ADDED, stableOutcome.getResult());

        byte[] stableFileBytesBefore = Files.readAllBytes(tempDir.resolve("stations/stable.yml"));
        int sizeBefore = repository.size();

        Path stationsDir = tempDir.resolve("stations");
        Files.setPosixFilePermissions(stationsDir, java.util.Collections.emptySet());

        try {
            StationRepository.AddOutcome failOutcome = await(repository.addAndSave(new StationRepository.RegisteredForge(
                "new-station", UUID.randomUUID(), "world", 30, 64, 40, "default")));
            assertEquals(StationRepository.AddResult.PERSISTENCE_FAILED, failOutcome.getResult());
            assertNotNull(failOutcome.getReference());
        } finally {
            Files.setPosixFilePermissions(stationsDir,
                java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        }

        byte[] stableFileBytesAfter = Files.readAllBytes(tempDir.resolve("stations/stable.yml"));
        assertArrayEquals(stableFileBytesBefore, stableFileBytesAfter);
        assertEquals(sizeBefore, repository.size());
        assertFalse(repository.findById("new-station").isPresent());
    }

    @Test
    void fatalStationDirectoryFailureCompletesExceptionallyAndPreservesPublishedIndex() throws Exception {
        Files.createDirectories(tempDir.resolve("stations"));
        UUID stableUuid = UUID.randomUUID();

        StationRepository.AddOutcome stableOutcome = await(repository.addAndSave(new StationRepository.RegisteredForge(
            "stable", stableUuid, "world", 10, 64, 20, "default")));
        assertEquals(StationRepository.AddResult.ADDED, stableOutcome.getResult());

        Files.delete(tempDir.resolve("stations/stable.yml"));
        Files.delete(tempDir.resolve("stations"));
        Files.write(tempDir.resolve("stations"), java.util.Collections.singletonList("not-a-directory"));

        ControlledSchedulerBridge controlled = new ControlledSchedulerBridge();
        StationRepository freshRepo = new StationRepository(pluginWithLogger(), controlled, tempDir);
        CompletableFuture<StationRepository.StationLoadReport> loadFuture = freshRepo.loadAsync();
        controlled.runNext();

        try {
            await(loadFuture);
            fail("Expected exceptional completion");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }

        assertTrue(repository.findById("stable").isPresent());
        assertEquals(1, repository.size());
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
