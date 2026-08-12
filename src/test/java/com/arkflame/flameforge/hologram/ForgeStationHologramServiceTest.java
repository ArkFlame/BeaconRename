package com.arkflame.flameforge.hologram;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.persistence.StationRepository.RegisteredForge;
import com.arkflame.flameforge.text.TextRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeStationHologramServiceTest {

    private JavaPlugin plugin;
    private StationRepository repository;
    private ConfigService configService;
    private HologramProvider provider;
    private HologramProviderSelector selector;
    private HologramSettings settings;
    private World world;
    private MockedStatic<Bukkit> mockedBukkit;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        world = mock(World.class);
        mockedBukkit = Mockito.mockStatic(Bukkit.class);
        mockedBukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(world);
        mockedBukkit.when(() -> Bukkit.getWorld(any(java.util.UUID.class))).thenReturn(world);

        repository = mock(StationRepository.class);
        configService = mock(ConfigService.class);
        provider = mock(HologramProvider.class);
        selector = mock(HologramProviderSelector.class);
        when(provider.isAvailable()).thenReturn(true);
        settings = HologramSettings.fromConfig(Arrays.asList("FancyHolograms", "DecentHolograms"),
            true, 1.75, true, Collections.singletonList("Forge Station"));
    }

    @AfterEach
    void tearDown() {
        mockedBukkit.close();
    }

    @Test
    void startupAndStationChangesReconcileProviderOnCorrectScheduler() {
        RegisteredForge forge = new RegisteredForge("forge-id", null, "world", 10, 64, 10, "default");
        when(repository.snapshotSortedById()).thenReturn(Collections.singletonList(forge));
        TrackingScheduler scheduler = new TrackingScheduler();
        AtomicReference<ExecutionScope> upsertScope = new AtomicReference<>();
        AtomicReference<ExecutionScope> removeScope = new AtomicReference<>();
        doAnswer(invocation -> {
            upsertScope.set(scheduler.scope.get());
            return null;
        }).when(provider).upsert(any(ForgeHologram.class));
        doAnswer(invocation -> {
            removeScope.set(scheduler.scope.get());
            return null;
        }).when(provider).remove(anyString());

        ForgeStationHologramService service = new ForgeStationHologramService(plugin, scheduler, repository,
            configService, new TextRenderer(), selector, provider, settings);
        service.reconcileStartup();
        service.stationAdd(forge);
        service.stationRemove(forge);

        assertEquals(ExecutionScope.REGION, upsertScope.get());
        assertEquals(ExecutionScope.REGION, removeScope.get());
        assertEquals(3, scheduler.globalRuns.get());
        assertEquals(3, scheduler.regionRuns.get());
        verify(provider, times(2)).upsert(any(ForgeHologram.class));
        verify(provider).remove("forge-id_hologram");
    }

    @Test
    void providerFailureCanRecoverWithoutLeakingHolograms() {
        when(provider.isAvailable()).thenReturn(false);
        HologramProvider unavailable = mock(HologramProvider.class);
        when(unavailable.isAvailable()).thenReturn(false);
        HologramProvider replacement = mock(HologramProvider.class);
        when(replacement.isAvailable()).thenReturn(true);
        when(selector.select(settings)).thenReturn(unavailable, replacement);
        RegisteredForge forge = new RegisteredForge("forge-id", null, "world", 10, 64, 10, "default");
        when(repository.snapshotSortedById()).thenReturn(Collections.singletonList(forge));

        ForgeStationHologramService service = new ForgeStationHologramService(plugin, new ImmediateScheduler(),
            repository, configService, new TextRenderer(), selector, provider, settings);
        service.reconcileStartup();
        service.reconcileStartup();
        service.reconcileStartup();

        verify(replacement, times(1)).upsert(any(ForgeHologram.class));
        verify(unavailable, never()).upsert(any(ForgeHologram.class));
        verify(provider, never()).upsert(any(ForgeHologram.class));
    }

    private enum ExecutionScope { NONE, GLOBAL, REGION }

    private enum TaskHandleStub implements TaskHandle {
        INSTANCE;

        @Override public void cancel() {}
        @Override public boolean isCancelled() { return false; }
    }

    private static class ImmediateScheduler implements SchedulerBridge {
        @Override public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runEntity(org.bukkit.entity.Entity entity, Runnable task, Runnable retire) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runEntityLater(org.bukkit.entity.Entity entity, Runnable task, Runnable retire, long delay) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runRegion(Location location, Runnable task) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runRegionLater(Location location, Runnable task, long delay) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public TaskHandle runAsync(JavaPlugin plugin, Runnable task) { task.run(); return TaskHandleStub.INSTANCE; }
        @Override public void cancelAll(JavaPlugin plugin) {}
        @Override public boolean isFolia() { return false; }
    }

    private static final class TrackingScheduler extends ImmediateScheduler {
        private final AtomicReference<ExecutionScope> scope = new AtomicReference<>(ExecutionScope.NONE);
        private final AtomicInteger globalRuns = new AtomicInteger();
        private final AtomicInteger regionRuns = new AtomicInteger();

        @Override public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
            globalRuns.incrementAndGet();
            scope.set(ExecutionScope.GLOBAL);
            task.run();
            scope.set(ExecutionScope.NONE);
            return TaskHandleStub.INSTANCE;
        }

        @Override public TaskHandle runRegion(Location location, Runnable task) {
            regionRuns.incrementAndGet();
            scope.set(ExecutionScope.REGION);
            task.run();
            scope.set(ExecutionScope.NONE);
            return TaskHandleStub.INSTANCE;
        }
    }
}
