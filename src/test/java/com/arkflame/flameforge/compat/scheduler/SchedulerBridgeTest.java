package com.arkflame.flameforge.compat.scheduler;

import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulerBridgeTest {

    private FakeSchedulerBridge schedulerBridge;

    @BeforeEach
    void setUp() {
        schedulerBridge = new FakeSchedulerBridge();
    }

    @Test
    void fakeSchedulerExecutesGlobalEntityRegionAndAsyncTasks() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Entity entity = mock(Entity.class);
        Location location = mock(Location.class);
        Runnable task = mock(Runnable.class);

        TaskHandle globalHandle = schedulerBridge.runGlobal(plugin, task);
        verify(task, times(1)).run();
        assertNotNull(globalHandle);

        task = mock(Runnable.class);
        TaskHandle entityHandle = schedulerBridge.runEntity(entity, task, mock(Runnable.class));
        verify(task, times(1)).run();
        assertNotNull(entityHandle);

        task = mock(Runnable.class);
        TaskHandle regionHandle = schedulerBridge.runRegion(location, task);
        verify(task, times(1)).run();
        assertNotNull(regionHandle);

        task = mock(Runnable.class);
        schedulerBridge.resetAsyncCount();
        TaskHandle asyncHandle = schedulerBridge.runAsync(plugin, task);
        assertEquals(1, schedulerBridge.getAsyncCallCount());
        assertNotNull(asyncHandle);
    }

    @Test
    void delayedMethodsPreserveRequestedOwnershipAndReturnCancelableHandles() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Entity entity = mock(Entity.class);
        Location location = mock(Location.class);

        TaskHandle globalLaterHandle = schedulerBridge.runGlobalLater(plugin, mock(Runnable.class), 10L);
        assertNotNull(globalLaterHandle);

        TaskHandle entityLaterHandle = schedulerBridge.runEntityLater(entity, mock(Runnable.class), mock(Runnable.class), 5L);
        assertNotNull(entityLaterHandle);

        TaskHandle regionLaterHandle = schedulerBridge.runRegionLater(location, mock(Runnable.class), 20L);
        assertNotNull(regionLaterHandle);
    }

    @Test
    void cancelAllAndRepeatedHandleCancellationAreIdempotent() {
        JavaPlugin plugin = mock(JavaPlugin.class);

        TaskHandle handle1 = schedulerBridge.runGlobal(plugin, mock(Runnable.class));
        TaskHandle handle2 = schedulerBridge.runGlobal(plugin, mock(Runnable.class));

        assertDoesNotThrow(() -> schedulerBridge.cancelAll(plugin));

        handle1.cancel();
        assertTrue(handle1.isCancelled());
        assertDoesNotThrow(() -> handle1.cancel());
        assertDoesNotThrow(() -> handle1.cancel());

        handle2.cancel();
        assertTrue(handle2.isCancelled());
        assertDoesNotThrow(() -> handle2.cancel());
    }

    @Test
    void fakeSchedulerReportsClassicPlatform() {
        assertFalse(schedulerBridge.isFolia());
    }

    @Test
    void nullPluginTaskEntityAndLocationArgumentsAreRejected() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Entity entity = mock(Entity.class);
        Location location = mock(Location.class);
        Runnable task = mock(Runnable.class);
        Runnable retireCallback = mock(Runnable.class);

        assertThrows(IllegalArgumentException.class, () -> schedulerBridge.runGlobal(plugin, null));
        assertThrows(IllegalArgumentException.class, () -> schedulerBridge.runGlobalLater(plugin, null, 10L));
        assertThrows(IllegalArgumentException.class, () -> schedulerBridge.runEntity(entity, null, retireCallback));
        assertThrows(IllegalArgumentException.class, () -> schedulerBridge.runEntity(entity, task, null));
        assertThrows(IllegalArgumentException.class, () -> schedulerBridge.runRegion(location, null));
        assertThrows(IllegalArgumentException.class, () -> schedulerBridge.runRegionLater(location, null, 20L));
    }

    @Test
    void negativeDelayIsRejected() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Entity entity = mock(Entity.class);
        Location location = mock(Location.class);
        Runnable task = mock(Runnable.class);
        Runnable retireCallback = mock(Runnable.class);

        assertThrows(IllegalArgumentException.class, () -> {
            schedulerBridge.runGlobalLater(plugin, task, -1L);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            schedulerBridge.runEntityLater(entity, task, retireCallback, -5L);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            schedulerBridge.runRegionLater(location, task, -20L);
        });
    }
}
