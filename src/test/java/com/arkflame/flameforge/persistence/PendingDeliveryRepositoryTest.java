package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.testfakes.MockJavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PendingDeliveryRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAsyncSerializesPendingDeliveryWrites() throws Exception {
        ControlledSchedulerBridge scheduler = new ControlledSchedulerBridge();
        PendingDeliveryRepository repository = new PendingDeliveryRepository(
            MockJavaPlugin.createMockPlugin(), scheduler, tempDir);
        repository.addDelivery(delivery("first"));

        CompletableFuture<Void> first = repository.saveAsync();
        CompletableFuture<Void> second = repository.saveAsync();
        ArrayList<String> completions = new ArrayList<>();
        first.whenComplete((ignored, failure) -> completions.add("first"));
        second.whenComplete((ignored, failure) -> completions.add("second"));

        assertSame(second, repository.currentWrite());
        assertFalse(first.isDone());
        assertFalse(second.isDone());
        assertEquals(1, scheduler.queuedAsyncTasks());

        scheduler.runNext();
        await(first);
        assertEquals(Arrays.asList("first"), completions);
        assertFalse(second.isDone());
        assertEquals(1, scheduler.queuedAsyncTasks());

        scheduler.runNext();
        await(second);
        assertEquals(Arrays.asList("first", "second"), completions);
        assertSame(second, repository.currentWrite());
    }

    private PendingDelivery delivery(String id) {
        return new PendingDelivery(id, UUID.randomUUID(), 10L,
            java.util.Collections.<String, Object>emptyMap(), java.util.Collections.<String>emptyList());
    }

    private static void await(CompletableFuture<Void> future) throws Exception {
        future.get(1, TimeUnit.SECONDS);
    }
}
