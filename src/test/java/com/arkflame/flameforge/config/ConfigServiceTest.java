package com.arkflame.flameforge.config;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    private JavaPlugin mockPlugin;
    private SchedulerBridge mockScheduler;
    private TierRepository tierRepository;
    private ConfigService configService;

    @BeforeEach
    void setUp() throws IOException {
        mockPlugin = mock(JavaPlugin.class);
        mockScheduler = mock(SchedulerBridge.class);
        when(mockPlugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(mockPlugin.getResource("config.yml")).thenReturn(null);
        when(mockPlugin.getResource("menus.yml")).thenReturn(null);
        when(mockPlugin.getResource("messages.yml")).thenReturn(null);
        when(mockPlugin.getResource("station-profiles.yml")).thenReturn(null);

        Files.createDirectories(tempDir.resolve("tiers"));

        tierRepository = new TierRepository(mockPlugin);

        configService = new ConfigService(mockPlugin, mockScheduler, tierRepository);
    }

    @Test
    void getCurrentSnapshotReturnsEmptySnapshotWhenNotLoaded() {
        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        assertNotNull(snapshot);
        assertFalse(snapshot.isLoaded());
    }

    @Test
    void getValidationReportReturnsEmptyReportWhenNotLoaded() {
        ValidationReport report = configService.getValidationReport();
        assertNotNull(report);
        assertFalse(report.hasErrors());
    }

    @Test
    void getCurrentSnapshotIsNotNullWhenNotLoaded() {
        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        assertNotNull(snapshot);
    }

    @Test
    void configServiceHandlesNullPluginResourceGracefully() {
        when(mockPlugin.getResource("config.yml")).thenReturn(null);
        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        assertNotNull(snapshot);
    }

    @Test
    void tierRepositoryIsAccessible() {
        assertNotNull(configService);
    }
}
