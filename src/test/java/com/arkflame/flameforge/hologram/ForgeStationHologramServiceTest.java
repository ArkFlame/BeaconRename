package com.arkflame.flameforge.hologram;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.persistence.StationRepository.RegisteredForge;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeStationHologramServiceTest {

    private ForgeStationHologramService service;
    private JavaPlugin mockPlugin;
    private FakeTestSchedulerBridge scheduler;
    private StationRepository mockRepo;
    private ConfigService mockConfigService;
    private HologramProvider mockProvider;
    private HologramProviderSelector mockSelector;
    private HologramSettings settings;
    private Server mockServer;
    private World mockWorld;
    private MockedStatic<Bukkit> mockedBukkit;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(JavaPlugin.class);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("test"));
        mockServer = mock(Server.class);
        when(mockPlugin.getServer()).thenReturn(mockServer);

        mockWorld = mock(World.class);
        mockedBukkit = Mockito.mockStatic(Bukkit.class);
        mockedBukkit.when(() -> Bukkit.getWorld(any(UUID.class))).thenReturn(mockWorld);
        mockedBukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(mockWorld);

        scheduler = new FakeTestSchedulerBridge();
        mockRepo = mock(StationRepository.class);
        mockConfigService = mock(ConfigService.class);
        mockProvider = mock(HologramProvider.class);
        mockSelector = mock(HologramProviderSelector.class);

        when(mockProvider.isAvailable()).thenReturn(true);

        settings = HologramSettings.fromConfig(
            Arrays.asList("FancyHolograms", "DecentHolograms"),
            true,
            1.75,
            true,
            Arrays.asList("<gradient:#ff5f00:#ffd166><bold>Forge Station</bold></gradient>", "<gray>%forge_id%")
        );

        service = new ForgeStationHologramService(
            mockPlugin, scheduler, mockRepo, mockConfigService,
            new TextRenderer(), mockSelector, mockProvider, settings
        );
    }

    @AfterEach
    void tearDown() {
        mockedBukkit.close();
    }

    @Test
    void startupReselectsAvailableProviderAndHydratesExistingStationsExactlyOnce() {
        when(mockProvider.isAvailable()).thenReturn(false);
        HologramProvider availableReplacement = mock(HologramProvider.class);
        when(availableReplacement.isAvailable()).thenReturn(true);
        when(availableReplacement.getName()).thenReturn("DecentHolograms");
        when(availableReplacement.getVersion()).thenReturn("1.0.0");
        when(mockSelector.select(any())).thenReturn(availableReplacement);

        RegisteredForge forge = new RegisteredForge(
            "forge_id", null, "world", 10, 64, 10, "profile");
        when(mockRepo.snapshotSortedById()).thenReturn(Collections.singletonList(forge));

        AtomicReference<ForgeHologram> capturedHologram = new AtomicReference<>();
        doAnswer(inv -> {
            capturedHologram.set(inv.getArgument(0));
            return null;
        }).when(availableReplacement).upsert(any(ForgeHologram.class));

        service.reconcileStartup();
        service.reconcileStartup();

        verify(mockSelector, times(1)).select(any());

        ForgeHologram hologram = capturedHologram.get();
        assertNotNull(hologram);
        assertEquals("flameforge_forge_id", hologram.getId());
        Location loc = hologram.getLocation();
        assertEquals(10.5, loc.getX(), 0.001);
        assertEquals(65.75, loc.getY(), 0.001);
        assertEquals(10.5, loc.getZ(), 0.001);

        verify(availableReplacement, times(1)).upsert(any(ForgeHologram.class));
    }

    @Test
    void failedInitialReselectLeavesStartupRecoverable() {
        when(mockProvider.isAvailable()).thenReturn(false);
        HologramProvider noOpProvider = mock(HologramProvider.class);
        when(noOpProvider.isAvailable()).thenReturn(false);
        when(noOpProvider.getUnavailableReason()).thenReturn("disabled");
        HologramProvider availableReplacement = mock(HologramProvider.class);
        when(availableReplacement.isAvailable()).thenReturn(true);
        when(availableReplacement.getName()).thenReturn("DecentHolograms");
        when(availableReplacement.getVersion()).thenReturn("1.0.0");
        when(mockSelector.select(any())).thenReturn(noOpProvider, availableReplacement);

        RegisteredForge forge = new RegisteredForge(
            "forge_id", null, "world", 10, 64, 10, "profile");
        when(mockRepo.snapshotSortedById()).thenReturn(Collections.singletonList(forge));

        AtomicInteger upsertCount = new AtomicInteger(0);
        doAnswer(inv -> {
            upsertCount.incrementAndGet();
            return null;
        }).when(availableReplacement).upsert(any(ForgeHologram.class));

        service.reconcileStartup();
        assertEquals(0, upsertCount.get());

        service.reconcileStartup();
        assertEquals(0, upsertCount.get());

        service.reconcileStartup();
        assertEquals(0, upsertCount.get());
    }

    private enum TaskHandleStub implements TaskHandle {
        INSTANCE;
        @Override public void cancel() {}
        @Override public boolean isCancelled() { return false; }
    }

    private static class FakeTestSchedulerBridge implements SchedulerBridge {
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
