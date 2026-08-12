package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.testfakes.MockJavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void queueClosePreservesOrderingAndFailsBoundedlyWhenFull() throws Exception {
        ControlledSchedulerBridge scheduler = new ControlledSchedulerBridge();
        AuditLogService service = new AuditLogService(
            MockJavaPlugin.createMockPlugin(), scheduler, tempDir, 3);

        assertTrue(service.logAsync("first", "actor", "target", "one"));
        assertTrue(service.logAsync("second", "actor", "target", "two"));
        CompletableFuture<Void> close = service.closeAsync();

        Thread writer = scheduler.startNext();
        await(close);
        writer.join(1000);
        assertFalse(writer.isAlive());

        Path auditFile;
        try (java.util.stream.Stream<Path> files = Files.list(tempDir.resolve("audit"))) {
            auditFile = files.findFirst()
                .orElseThrow(() -> new AssertionError("audit file not created"));
        }
        List<String> entries = Files.readAllLines(auditFile, StandardCharsets.UTF_8);
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).contains("\"action\":\"first\""));
        assertTrue(entries.get(1).contains("\"action\":\"second\""));

        ControlledSchedulerBridge fullScheduler = new ControlledSchedulerBridge();
        AuditLogService fullService = new AuditLogService(
            MockJavaPlugin.createMockPlugin(), fullScheduler, tempDir, 1);
        assertTrue(fullService.logAsync("queued", "actor", "target", "details"));
        CompletableFuture<Void> fullClose = fullService.closeAsync();

        assertTrue(fullClose.isDone());
        assertFailed(fullClose, "Audit queue is full");
        assertEquals(1, fullScheduler.queuedAsyncTasks());
    }

    private static void await(CompletableFuture<Void> future) throws Exception {
        future.get(1, TimeUnit.SECONDS);
    }

    private static void assertFailed(CompletableFuture<Void> future, String message) {
        ExecutionException failure = assertThrows(ExecutionException.class, () ->
            future.get(1, TimeUnit.SECONDS));
        assertEquals(message, failure.getCause().getMessage());
    }
}
