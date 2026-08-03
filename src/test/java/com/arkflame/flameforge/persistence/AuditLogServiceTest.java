package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.testfakes.MockJavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void fullQueue_flushAndCloseFailImmediately() throws Exception {
        ControlledSchedulerBridge scheduler = new ControlledSchedulerBridge();
        AuditLogService service = new AuditLogService(
            MockJavaPlugin.createMockPlugin(), scheduler, tempDir, 1);

        assertTrue(service.logAsync("queued", "actor", "target", "details"));
        CompletableFuture<Void> flush = service.flushAsync();
        CompletableFuture<Void> close = service.closeAsync();

        assertTrue(flush.isDone());
        assertTrue(close.isDone());
        assertFailed(flush, "Audit queue is full");
        assertFailed(close, "Audit queue is full");
        assertEquals(1, scheduler.queuedAsyncTasks());
    }

    @Test
    void flushAndClose_acknowledgeInQueueOrder() throws Exception {
        ControlledSchedulerBridge scheduler = new ControlledSchedulerBridge();
        AuditLogService service = new AuditLogService(
            MockJavaPlugin.createMockPlugin(), scheduler, tempDir, 4);
        List<String> acknowledgements = Collections.synchronizedList(new ArrayList<String>());

        assertTrue(service.logAsync("first", "actor", "target", "one"));
        CompletableFuture<Void> flush = service.flushAsync();
        flush.whenComplete((ignored, failure) -> acknowledgements.add("flush"));
        assertTrue(service.logAsync("second", "actor", "target", "two"));
        CompletableFuture<Void> close = service.closeAsync();
        close.whenComplete((ignored, failure) -> acknowledgements.add("close"));

        Thread writer = scheduler.startNext();
        try {
            await(close);
        } finally {
            writer.join(1000);
            if (writer.isAlive()) {
                writer.interrupt();
            }
        }

        assertFalse(writer.isAlive());
        assertEquals(Arrays.asList("flush", "close"), acknowledgements);
        Path auditFile = tempDir.resolve("audit").resolve(
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".jsonl");
        List<String> entries = Files.readAllLines(auditFile, StandardCharsets.UTF_8);
        assertEquals(2, entries.size());
        assertTrue(entries.get(0).contains("\"action\":\"first\""));
        assertTrue(entries.get(1).contains("\"action\":\"second\""));
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
