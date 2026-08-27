package com.arkflame.flameforge.config;

import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    private JavaPlugin plugin;
    private ConfigService configService;

    @BeforeEach
    void setUp() throws IOException {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubBundledResource("config.yml");
        stubBundledResource("menus.yml");
        stubBundledResource("messages.yml");
        stubBundledResource("station-profiles.yml");
        stubBundledTiers();
        Files.createDirectories(tempDir.resolve("tiers"));
        configService = new ConfigService(plugin, new FakeSchedulerBridge(), new TierRepository(plugin));
    }

    @Test
    void loadPublishesValidSnapshotAndFallsBackFromInvalidOperatorTier() throws Exception {
        Files.write(tempDir.resolve("tiers").resolve("operator-invalid.yml"),
            ("schema-version: 2\nid: operator-invalid\nlevel: invalid\n")
                .getBytes(StandardCharsets.UTF_8));

        configService.initialLoad();

        assertTrue(configService.isLoaded());
        assertFalse(configService.hasValidationErrors());
        assertFalse(configService.getAllTiers().isEmpty());
        assertTrue(configService.getValidationReport().hasWarnings());
    }

    @Test
    void bundledPowerTraceDefaultsFalseWhenFallbackIsTrue() throws Exception {
        configService.initialLoad();

        assertFalse(configService.getCurrentSnapshot().getRootBoolean("forge.power-trace", true));
    }

    @Test
    void operatorPowerTracePublishesAfterInitialLoadAndReload() throws Exception {
        configService.initialLoad();

        assertFalse(configService.getCurrentSnapshot().getRootBoolean("forge.power-trace", true));

        Files.write(tempDir.resolve("config.yml"),
            "forge:\n  power-trace: true\n".getBytes(StandardCharsets.UTF_8));
        ConfigService.ReloadResult result = configService.reloadAsync().getNow(null);

        assertNotNull(result);
        assertEquals(ConfigService.ReloadResult.Status.APPLIED, result.getStatus());
        assertTrue(configService.getCurrentSnapshot().getRootBoolean("forge.power-trace", false));
    }

    @Test
    void validateDoesNotPublishCandidateSnapshot() throws Exception {
        configService.initialLoad();
        ConfigSnapshot before = configService.getCurrentSnapshot();

        Files.write(tempDir.resolve("tiers").resolve("operator-valid.yml"),
            ("schema-version: 2\nid: operator-valid\nlevel: 99\n")
                .getBytes(StandardCharsets.UTF_8));

        ConfigService.ValidationResult result = configService.validateAsync().getNow(null);

        assertNotNull(result);
        assertEquals(ConfigService.ValidationResult.Status.COMPLETED, result.getStatus());
        assertSame(before, configService.getCurrentSnapshot());
        assertFalse(configService.findTier("operator-valid").isPresent());
    }

    private void stubBundledTiers() {
        for (int level = 1; level <= 7; level++) {
            stubBundledResource("tiers/tier" + level + ".yml");
        }
    }

    private void stubBundledResource(String path) {
        InputStream source = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(source);
        byte[] bytes;
        try {
            bytes = readBytes(source);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
        when(plugin.getResource(path)).thenAnswer(invocation -> new ByteArrayInputStream(bytes.clone()));
    }

    private byte[] readBytes(InputStream source) throws IOException {
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
