package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.particle.ParticleBatch;
import com.arkflame.flameforge.compat.effect.particle.ParticleColor;
import com.arkflame.flameforge.compat.effect.particle.ParticleRequest;
import com.arkflame.flameforge.compat.effect.SoundResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.ForgeAnimationProfile;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
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
import org.mockito.ArgumentCaptor;
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
    private World world;
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

        service = new ForgeAnimationService(plugin, scheduler, particles, sounds, text, textRenderer,
            itemVisuals, new ForgeAnimationThemeResolver());

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.isOnline()).thenReturn(true);

        world = mock(World.class);
        when(world.getName()).thenReturn("world");
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
        verify(particles, atLeastOnce()).sendBatch(eq(owner), any(ParticleBatch.class));
        verify(itemVisuals, atLeastOnce()).spawn(eq("tx-flow"), eq(owner), any(ItemStack.class), any(Location.class));
        verify(itemVisuals, atLeastOnce()).move(eq("tx-flow"), any(Location.class));
    }

    @Test
    void cosmeticFailureDoesNotAbortForge() {
        doThrow(new RuntimeException("particle failure")).when(particles).sendBatch(
            any(), any(ParticleBatch.class));
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
        service = new ForgeAnimationService(plugin, scheduler, particles, sounds, text, textRenderer,
            itemVisuals, new ForgeAnimationThemeResolver());
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

    @Test
    void themedAnimationRejectsMissingRequiredValues() {
        ForgeVariant variant = variant();
        assertThrows(IllegalArgumentException.class, () -> service.playAnimation(
            "tx-null-station", owner, null, visualItem, profile(),
            ForgeOutcomeCategory.SUCCESS, variant, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.playAnimation(
            "tx-null-variant", owner, stationLocation, visualItem, profile(),
            ForgeOutcomeCategory.SUCCESS, null, null, null));
    }

    @Test
    void themedAnimationAcceptsNullableVariantForNonSuccess() {
        AnimationHandle handle = service.playAnimation(
            "tx-break", owner, stationLocation, visualItem, profile(),
            ForgeOutcomeCategory.BREAK, null, null, null);

        assertNotNull(handle);
        assertTrue(handle.isCompleted());
    }

    @Test
    void forgingItemFollowsExactOrbitAndRisePath() {
        Location start = ForgeAnimationService.computeForgingItemLocation(stationLocation, 0f);
        assertEquals(0.24, start.getX() - stationLocation.getX(), 1e-6);
        assertEquals(1.15, start.getY() - stationLocation.getY(), 1e-6);
        assertEquals(0.0, start.getZ() - stationLocation.getZ(), 1e-6);
        assertEquals(0.0f, start.getYaw(), 1e-6f);

        Location firstCircle = ForgeAnimationService.computeForgingItemLocation(stationLocation, 1f / 6f);
        assertEquals(-0.24 + 0.08 / 6.0, firstCircle.getX() - stationLocation.getX(), 1e-6);

        Location half = ForgeAnimationService.computeForgingItemLocation(stationLocation, 0.5f);
        assertEquals(-0.20, half.getX() - stationLocation.getX(), 1e-6);
        assertEquals(1.15 + 0.85 * 0.5, half.getY() - stationLocation.getY(), 1e-6);

        Location end = ForgeAnimationService.computeForgingItemLocation(stationLocation, 1f);
        assertEquals(0.16, end.getX() - stationLocation.getX(), 1e-6);
        assertEquals(2.0, end.getY() - stationLocation.getY(), 1e-6);
        assertEquals(0.0, end.getZ() - stationLocation.getZ(), 1e-6);
        assertEquals(1080.0f, end.getYaw(), 1e-6f);
    }

    @Test
    void revealStarOccursBeforeCompletionAndCleanupDestroysVisual() {
        scheduler = new TestSchedulerBridge(false);
        service = new ForgeAnimationService(plugin, scheduler, particles, sounds, text, textRenderer,
            itemVisuals, new ForgeAnimationThemeResolver());
        when(itemVisuals.spawn(any(), any(), any(), any())).thenReturn(true);

        List<String> events = new ArrayList<>();
        doAnswer(invocation -> {
            ParticleBatch batch = invocation.getArgument(1);
            for (ParticleRequest request : batch.getRequests()) {
                if (request.getPayload() instanceof ParticleRequest.Color) {
                    ParticleColor color = ((ParticleRequest.Color) request.getPayload()).getColor();
                    events.add("dust:" + color.getRed() + "," + color.getGreen() + "," + color.getBlue());
                }
            }
            return null;
        }).when(particles).sendBatch(any(), any(ParticleBatch.class));

        service.playAnimation("tx-star", owner, stationLocation, visualItem, profile(),
            tx -> events.add("complete"), null);
        scheduler.runAll();

        assertTrue(events.contains("dust:245,158,11"));
        assertTrue(events.indexOf("dust:245,158,11") < events.indexOf("complete"));
        verify(itemVisuals).destroy("tx-star");
    }

    @Test
    void completionCallbackDelayedByTenRevealTicks() {
        scheduler = new TestSchedulerBridge(false);
        service = new ForgeAnimationService(plugin, scheduler, particles, sounds, text, textRenderer,
            itemVisuals, new ForgeAnimationThemeResolver());
        when(itemVisuals.spawn(any(), any(), any(), any())).thenReturn(true);

        service.playAnimation("tx-delay", owner, stationLocation, visualItem, profile(), null, null);

        assertFalse(scheduler.entityLaterDelays.isEmpty());
        long completionDelay = scheduler.entityLaterDelays.get(scheduler.entityLaterDelays.size() - 1);
        assertEquals(profile().getDurationTicks() + 10L, completionDelay);
    }

    @Test
    void themeAuraDustUsesResultPalette() {
        ForgeVariant electric = variantWithPower(ForgePowerDefinition.PowerType.ON_HIT_CHAIN_DAMAGE);
        service.playAnimation("tx-electric", owner, stationLocation, visualItem, profile(),
            ForgeOutcomeCategory.SUCCESS, electric, null, null);

        ArgumentCaptor<ParticleBatch> batches = ArgumentCaptor.forClass(ParticleBatch.class);
        verify(particles, atLeastOnce()).sendBatch(eq(owner), batches.capture());
        assertTrue(containsColor(batches.getAllValues(), 250, 204, 21));
    }

    private boolean containsColor(List<ParticleBatch> batches, int red, int green, int blue) {
        for (ParticleBatch batch : batches) {
            for (ParticleRequest request : batch.getRequests()) {
                if (request.getPayload() instanceof ParticleRequest.Color) {
                    ParticleColor color = ((ParticleRequest.Color) request.getPayload()).getColor();
                    if (color.getRed() == red && color.getGreen() == green && color.getBlue() == blue) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private ForgeAnimationProfile profile() {
        return new ForgeAnimationProfile(
            8, 4, null,
            new ForgeAnimationProfile.ChargeParticle(Collections.singletonList("flame"), 5, BigDecimal.ONE),
            null, null, null, null);
    }

    private ForgeVariant variant() {
        return new ForgeVariant("variant", "Variant", Collections.emptyList(), 1.0, null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private ForgeVariant variantWithPower(ForgePowerDefinition.PowerType type) {
        return new ForgeVariant("electric-variant", "Electric", Collections.emptyList(), 1.0, null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.singletonList(new ForgePowerDefinition("p1", type, 0, 0, BigDecimal.ONE,
                Collections.emptyList(), 0, 0, 0, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO,
                Collections.singletonList(ForgePowerDefinition.ActivationSlot.MAINHAND))));
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
        private final List<Long> entityLaterDelays = new ArrayList<>();
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
            entityLaterDelays.add(delay);
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
