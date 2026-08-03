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
    void unavailableProviderSkipsAllOperations() {
        when(mockProvider.isAvailable()).thenReturn(false);
        when(mockRepo.snapshotSortedById()).thenReturn(Collections.emptyList());

        AtomicInteger upsertCount = new AtomicInteger(0);
        AtomicInteger removeCount = new AtomicInteger(0);
        AtomicReference<ForgeHologram> capturedHologram = new AtomicReference<>();
        doAnswer(inv -> {
            upsertCount.incrementAndGet();
            capturedHologram.set(inv.getArgument(0));
            return null;
        }).when(mockProvider).upsert(any(ForgeHologram.class));
        doAnswer(inv -> {
            removeCount.incrementAndGet();
            return null;
        }).when(mockProvider).remove(anyString());

        RegisteredForge forge = new RegisteredForge(
            "forge_id", null, "world", 10, 64, 10, "profile");
        service.onStationAdded(forge);
        assertEquals(0, upsertCount.get());

        when(mockProvider.isAvailable()).thenReturn(true);
        when(mockSelector.select(any())).thenReturn(mockProvider);
        when(mockRepo.snapshotSortedById()).thenReturn(Collections.singletonList(forge));
        doAnswer(inv -> {
            upsertCount.incrementAndGet();
            capturedHologram.set(inv.getArgument(0));
            return null;
        }).when(mockProvider).upsert(any(ForgeHologram.class));

        service.reconcileStartup();
        assertEquals(1, upsertCount.get());

        ForgeHologram hologram = capturedHologram.get();
        assertNotNull(hologram);
        assertEquals("flameforge_forge_id", hologram.getId());
        Location loc = hologram.getLocation();
        assertEquals(10.5, loc.getX(), 0.001);
        assertEquals(65.75, loc.getY(), 0.001);
        assertEquals(10.5, loc.getZ(), 0.001);
        assertEquals(2, hologram.getMiniMessageLines().size());
        assertEquals(2, hologram.getLegacyLines().size());
        assertFalse(hologram.getMiniMessageLines().isEmpty());
        assertFalse(hologram.getLegacyLines().isEmpty());

        AtomicReference<ForgeHologram> addHologram = new AtomicReference<>();
        reset(mockProvider);
        when(mockProvider.isAvailable()).thenReturn(true);
        when(mockRepo.snapshotSortedById()).thenReturn(Collections.emptyList());
        doAnswer(inv -> {
            addHologram.set(inv.getArgument(0));
            return null;
        }).when(mockProvider).upsert(any(ForgeHologram.class));
        service.onStationAdded(new RegisteredForge(
            "forge2", null, "world", 20, 70, 20, "profile2"));
        assertNotNull(addHologram.get());
        Location addLoc = addHologram.get().getLocation();
        assertEquals(20.5, addLoc.getX(), 0.001);
        assertEquals(71.75, addLoc.getY(), 0.001);
        assertEquals(20.5, addLoc.getZ(), 0.001);
    }

    @Test
    void missingMappingSkipsRemoveAndUpdate() {
        when(mockProvider.isAvailable()).thenReturn(true);
        when(mockRepo.snapshotSortedById()).thenReturn(Collections.emptyList());

        AtomicInteger upsertCount = new AtomicInteger(0);
        AtomicInteger removeCount = new AtomicInteger(0);
        doAnswer(inv -> {
            upsertCount.incrementAndGet();
            return null;
        }).when(mockProvider).upsert(any(ForgeHologram.class));
        doAnswer(inv -> {
            removeCount.incrementAndGet();
            return null;
        }).when(mockProvider).remove(anyString());

        RegisteredForge unknownForge = new RegisteredForge(
            "unknown-forge", null, "world", 10, 64, 10, "default");
        service.onStationRemoved(unknownForge);
        assertEquals(0, removeCount.get());

        Location location = new Location(mockWorld, 0.5, 64.0, 0.5);
        service.updateHologram("unknown-forge", location, Arrays.asList("Line 1"));
        assertEquals(0, upsertCount.get());

        HologramProvider newProvider = mock(HologramProvider.class);
        when(newProvider.isAvailable()).thenReturn(true);
        when(mockSelector.select(any())).thenReturn(newProvider);
        ConfigSnapshot mockSnapshot = mock(ConfigSnapshot.class);
        when(mockConfigService.getCurrentSnapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.getRootBoolean(eq("holograms.enabled"), anyBoolean())).thenReturn(true);
        when(mockSnapshot.getRootStringList("holograms.provider-order")).thenReturn(Arrays.asList("FancyHolograms"));
        when(mockSnapshot.getRootDouble(eq("holograms.offset-y"), anyDouble())).thenReturn(2.0);
        when(mockSnapshot.getRootBoolean(eq("holograms.transparent-background"), anyBoolean())).thenReturn(true);
        when(mockSnapshot.getRootStringList("holograms.lines")).thenReturn(Arrays.asList("<gold>Test"));

        AtomicInteger newUpsertCount = new AtomicInteger(0);
        doAnswer(inv -> {
            newUpsertCount.incrementAndGet();
            return null;
        }).when(newProvider).upsert(any(ForgeHologram.class));
        doAnswer(inv -> {
            removeCount.incrementAndGet();
            return null;
        }).when(newProvider).remove(anyString());

        RegisteredForge knownForge = new RegisteredForge(
            "known-forge", null, "world", 15, 65, 15, "default");
        service.onStationAdded(knownForge);
        assertEquals(1, upsertCount.get());
        reset(mockRepo);
        when(mockRepo.snapshotSortedById()).thenReturn(Collections.singletonList(knownForge));
        when(mockRepo.findById(anyString())).thenReturn(java.util.Optional.of(knownForge));

        service.reload();
        assertTrue(removeCount.get() > 0 || newUpsertCount.get() > 0);
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
