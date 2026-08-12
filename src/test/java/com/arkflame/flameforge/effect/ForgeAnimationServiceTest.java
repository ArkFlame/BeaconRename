package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.SoundResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.ForgeAnimationProfile;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.text.TextRenderer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeAnimationServiceTest {
    private ForgeAnimationService service;
    private JavaPlugin plugin;
    private TestSchedulerBridge scheduler;
    private ParticleBridge particles;
    private SoundResolver sounds;
    private TextBridge text;
    private TextRenderer textRenderer;
    private ForgeItemVisualService itemVisuals;
    private Player owner;
    private Location stationLocation;
    private ItemStack visualItem;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-forge-animation"));

        scheduler = new TestSchedulerBridge(true);
        particles = mock(ParticleBridge.class);
        sounds = mock(SoundResolver.class);
        text = mock(TextBridge.class);
        textRenderer = mock(TextRenderer.class);
        itemVisuals = mock(ForgeItemVisualService.class);

        service = new ForgeAnimationService(plugin, scheduler, particles, sounds, text, textRenderer, itemVisuals);

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.isOnline()).thenReturn(true);

        World world = mock(World.class);
        stationLocation = new Location(world, 0, 64, 0);
        visualItem = new ItemStack(org.bukkit.Material.DIAMOND_SWORD);
    }

    @Test
    void forgeAnimationRunsAndCompletes() {
        when(itemVisuals.spawn(any(), any(), any(), any())).thenReturn(true);

        AnimationHandle handle = service.playAnimation(
            "tx-flow", owner, stationLocation, visualItem,
            profile(), null, null);

        assertNotNull(handle);
        assertTrue(handle.isCompleted());
        assertTrue(scheduler.usedRunEntityLater);
        assertFalse(scheduler.usedRunRegionLater);
        verify(particles, atLeastOnce()).sendToPlayer(eq(owner), anyString(), any(Location.class),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt());
        verify(itemVisuals, atLeastOnce()).spawn(eq("tx-flow"), eq(owner), any(ItemStack.class), any(Location.class));
        verify(itemVisuals, atLeastOnce()).move(eq("tx-flow"), any(Location.class));
    }

    @Test
    void cosmeticFailureDoesNotAbortForge() {
        doThrow(new RuntimeException("particle failure")).when(particles).sendToPlayer(
            any(), anyString(), any(Location.class), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt());
        when(itemVisuals.spawn(any(), any(), any(), any())).thenReturn(false);

        AnimationHandle handle = service.playAnimation(
            "tx-cosmetic-failure", owner, stationLocation, visualItem,
            profile(), null, null);

        assertNotNull(handle);
        assertTrue(handle.isCompleted());
        verify(itemVisuals, atLeastOnce()).spawn(eq("tx-cosmetic-failure"), eq(owner),
            any(ItemStack.class), any(Location.class));
    }

    @Test
    void cancelOrShutdownCleansForgeVisuals() {
        scheduler = new TestSchedulerBridge(false);
        service = new ForgeAnimationService(plugin, scheduler, particles, sounds, text, textRenderer, itemVisuals);
        when(itemVisuals.spawn(any(), any(), any(), any())).thenReturn(true);

        service.playAnimation("tx-complete", owner, stationLocation, visualItem,
            profile(), null, null);
        assertTrue(service.isRunning("tx-complete"));
        scheduler.runAll();

        assertFalse(service.isRunning("tx-complete"));
        verify(itemVisuals, atLeastOnce()).destroy("tx-complete");

        service.playAnimation("tx-cancel", owner, stationLocation, visualItem,
            profile(), null, null);
        assertTrue(service.isRunning("tx-cancel"));
        service.cancelAnimation("tx-cancel", false, null);

        assertFalse(service.isRunning("tx-cancel"));
        verify(itemVisuals, atLeastOnce()).destroy("tx-cancel");

        service.playAnimation("tx-shutdown-one", owner, stationLocation, visualItem,
            profile(), null, null);
        service.playAnimation("tx-shutdown-two", owner, stationLocation, visualItem,
            profile(), null, null);

        service.shutdown();

        assertFalse(service.isRunning("tx-shutdown-one"));
        assertFalse(service.isRunning("tx-shutdown-two"));
        verify(itemVisuals).destroyAll();
    }

    private ForgeAnimationProfile profile() {
        return new ForgeAnimationProfile(
            8, 4, null,
            new ForgeAnimationProfile.ChargeParticle(Collections.singletonList("flame"), 5, BigDecimal.ONE),
            null, null, null, null);
    }

    private static final class PendingTask {
        private final TrackingTaskHandle handle;
        private final Runnable task;

        private PendingTask(TrackingTaskHandle handle, Runnable task) {
            this.handle = handle;
            this.task = task;
        }
    }

    private static final class TrackingTaskHandle implements TaskHandle {
        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class TestSchedulerBridge implements SchedulerBridge {
        private final boolean executeImmediately;
        private final List<PendingTask> pendingTasks = new ArrayList<>();
        private final List<TrackingTaskHandle> issuedTaskHandles = new ArrayList<>();
        private boolean usedRunEntityLater;
        private boolean usedRunRegionLater;

        private TestSchedulerBridge(boolean executeImmediately) {
            this.executeImmediately = executeImmediately;
        }

        @Override
        public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
            return issue(task);
        }

        @Override
        public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
            return issue(task);
        }

        @Override
        public TaskHandle runEntity(Entity entity, Runnable task, Runnable retireCallback) {
            return issue(task);
        }

        @Override
        public TaskHandle runEntityLater(Entity entity, Runnable task, Runnable retireCallback, long delay) {
            usedRunEntityLater = true;
            return issue(task);
        }

        @Override
        public TaskHandle runRegion(Location location, Runnable task) {
            return issue(task);
        }

        @Override
        public TaskHandle runRegionLater(Location location, Runnable task, long delay) {
            usedRunRegionLater = true;
            return issue(task);
        }

        @Override
        public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
            return issue(task);
        }

        private TaskHandle issue(Runnable task) {
            TrackingTaskHandle handle = new TrackingTaskHandle();
            issuedTaskHandles.add(handle);
            if (executeImmediately) {
                task.run();
            } else {
                pendingTasks.add(new PendingTask(handle, task));
            }
            return handle;
        }

        private void runAll() {
            for (int index = 0; index < pendingTasks.size(); index++) {
                PendingTask pending = pendingTasks.get(index);
                if (!pending.handle.isCancelled()) {
                    pending.task.run();
                }
            }
        }

        @Override
        public void cancelAll(JavaPlugin plugin) {
        }

        @Override
        public boolean isFolia() {
            return false;
        }
    }
}
