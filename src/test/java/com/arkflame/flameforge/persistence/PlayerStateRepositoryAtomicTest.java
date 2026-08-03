package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.persistence.PlayerStateRepository.PlayerState;
import com.arkflame.flameforge.testfakes.MockJavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStateRepositoryAtomicTest {

    @TempDir
    Path tempDir;

    @Test
    void loadAsyncReturnsBeforeQueuedExecutorRuns() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path folder = Files.createDirectories(tempDir.resolve("player-data"));
        Files.write(folder.resolve(uuid + ".yml"),
            java.util.Arrays.asList("tier: 4", "pityCooldown: 12"), StandardCharsets.UTF_8);
        ControlledSchedulerBridge scheduler = new ControlledSchedulerBridge();
        PlayerStateRepository repository = new PlayerStateRepository(
            MockJavaPlugin.createMockPlugin(), scheduler, tempDir);

        CompletableFuture<Void> load = repository.loadAllAsync();

        assertFalse(load.isDone());
        assertEquals(1, scheduler.queuedAsyncTasks());
        assertEquals(new PlayerState(uuid, 0, 0L), repository.getSnapshot(uuid));

        scheduler.runNext();
        await(load);
        assertEquals(new PlayerState(uuid, 4, 12L), repository.getSnapshot(uuid));
    }

    @Test
    void topLevelScanFailureCompletesExceptionallyWithoutPublishingPartialState() throws Exception {
        Files.write(tempDir.resolve("player-data"),
            java.util.Arrays.asList("not-a-directory"), StandardCharsets.UTF_8);
        ControlledSchedulerBridge scheduler = new ControlledSchedulerBridge();
        PlayerStateRepository repository = new PlayerStateRepository(
            MockJavaPlugin.createMockPlugin(), scheduler, tempDir);

        CompletableFuture<Void> load = repository.loadAllAsync();
        scheduler.runNext();

        ExecutionException failure = assertThrows(ExecutionException.class, () ->
            load.get(1, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    @Test
    void malformedPlayerFileIsSkippedWhileValidFilesPublishAtomically() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path folder = Files.createDirectories(tempDir.resolve("player-data"));
        Files.write(folder.resolve(uuid + ".yml"),
            java.util.Arrays.asList("tier: 7", "pityCooldown: 99"), StandardCharsets.UTF_8);
        Files.write(folder.resolve("malformed.yml"),
            java.util.Arrays.asList("tier: not-a-number"), StandardCharsets.UTF_8);
        ControlledSchedulerBridge scheduler = new ControlledSchedulerBridge();
        PlayerStateRepository repository = new PlayerStateRepository(
            MockJavaPlugin.createMockPlugin(), scheduler, tempDir);

        CompletableFuture<Void> load = repository.loadAllAsync();
        scheduler.runNext();
        await(load);

        assertEquals(new PlayerState(uuid, 7, 99L), repository.getSnapshot(uuid));
    }

    private static void await(CompletableFuture<Void> future) throws Exception {
        future.get(1, TimeUnit.SECONDS);
    }
}
