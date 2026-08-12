package com.arkflame.flameforge.compat.scheduler;

import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import com.arkflame.flameforge.testfakes.TaskHandleStub;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SchedulerBridgeTest {

    private FakeSchedulerBridge schedulerBridge;

    @BeforeEach
    void setUp() {
        schedulerBridge = new FakeSchedulerBridge();
        ((TaskHandleStub) TaskHandleStub.INSTANCE).reset();
    }

    @Test
    void schedulerExecutesAllOwnershipScopes() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Entity entity = mock(Entity.class);
        Location location = mock(Location.class);

        Runnable global = mock(Runnable.class);
        Runnable entityTask = mock(Runnable.class);
        Runnable region = mock(Runnable.class);
        Runnable async = mock(Runnable.class);

        assertNotNull(schedulerBridge.runGlobal(plugin, global));
        assertNotNull(schedulerBridge.runEntity(entity, entityTask, mock(Runnable.class)));
        assertNotNull(schedulerBridge.runRegion(location, region));
        assertNotNull(schedulerBridge.runAsync(plugin, async));

        verify(global).run();
        verify(entityTask).run();
        verify(region).run();
        verify(async).run();
    }

    @Test
    void delayedTasksReturnCancelableHandlesAndRejectNegativeDelay() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Entity entity = mock(Entity.class);
        Location location = mock(Location.class);
        Runnable task = mock(Runnable.class);
        Runnable retireCallback = mock(Runnable.class);

        TaskHandle global = schedulerBridge.runGlobalLater(plugin, task, 1L);
        TaskHandle entityHandle = schedulerBridge.runEntityLater(entity, task, retireCallback, 2L);
        TaskHandle region = schedulerBridge.runRegionLater(location, task, 3L);
        assertNotNull(global);
        assertNotNull(entityHandle);
        assertNotNull(region);

        global.cancel();
        assertTrue(global.isCancelled());

        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runGlobalLater(plugin, task, -1L));
        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runEntityLater(entity, task, retireCallback, -1L));
        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runRegionLater(location, task, -1L));
    }

    @Test
    void invalidRequiredArgumentsAreRejected() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Entity entity = mock(Entity.class);
        Location location = mock(Location.class);
        Runnable task = mock(Runnable.class);
        Runnable retireCallback = mock(Runnable.class);

        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runGlobal(plugin, null));
        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runGlobalLater(null, task, 1L));
        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runEntity(null, task, retireCallback));
        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runEntity(entity, null, retireCallback));
        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runEntity(entity, task, null));
        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runRegion(null, task));
        assertThrows(IllegalArgumentException.class,
            () -> schedulerBridge.runRegionLater(location, null, 1L));
    }
}
