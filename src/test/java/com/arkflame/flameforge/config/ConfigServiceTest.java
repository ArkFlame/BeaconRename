package com.arkflame.flameforge.config;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    private JavaPlugin mockPlugin;
    private SchedulerBridge controlledScheduler;
    private TierRepository tierRepository;
    private ConfigService configService;

    private static class ControlledSchedulerBridge implements SchedulerBridge {
        private final AtomicReference<Runnable> lastTask = new AtomicReference<>();

        @Override
        public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
            lastTask.set(task);
            task.run();
            return mock(TaskHandle.class);
        }

        @Override
        public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
            return runAsync(plugin, task);
        }

        @Override
        public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
            return runAsync(plugin, task);
        }

        @Override
        public TaskHandle runEntity(org.bukkit.entity.Entity entity, Runnable runnable, Runnable retireCallback) {
            return runAsync(null, runnable);
        }

        @Override
        public TaskHandle runEntityLater(org.bukkit.entity.Entity entity, Runnable runnable, Runnable retireCallback, long delay) {
            return runAsync(null, runnable);
        }

        @Override
        public TaskHandle runRegion(org.bukkit.Location location, Runnable task) {
            return runAsync(null, task);
        }

        @Override
        public TaskHandle runRegionLater(org.bukkit.Location location, Runnable task, long delay) {
            return runAsync(null, task);
        }

        @Override
        public void cancelAll(JavaPlugin plugin) {
        }

        @Override
        public boolean isFolia() {
            return false;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        mockPlugin = mock(JavaPlugin.class);
        controlledScheduler = new ControlledSchedulerBridge();
        when(mockPlugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubBundledResource("config.yml");
        stubBundledResource("menus.yml");
        stubBundledResource("messages.yml");
        stubBundledResource("station-profiles.yml");

        Files.createDirectories(tempDir.resolve("tiers"));

        tierRepository = new TierRepository(mockPlugin);

        configService = new ConfigService(mockPlugin, controlledScheduler, tierRepository);
    }

    private void stubBundledResource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "Bundled resource should exist: " + path);
        byte[] bytes;
        try {
            bytes = toByteArray(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertTrue(bytes.length > 0, "Bundled resource should not be empty: " + path);
        when(mockPlugin.getResource(path)).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
    }

    private void setupBundledTiers() {
        for (int i = 1; i <= 7; i++) {
            stubBundledResource("tiers/tier" + i + ".yml");
        }
    }

    private byte[] toByteArray(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    @Test
    void initialLoadUsesBundledBaselineWhenOperatorTierIsSchemaOne() throws Exception {
        setupBundledTiers();

        Files.createDirectories(tempDir.resolve("tiers"));

        String schemaOneTier = "schema-version: 1\nid: tier1\nlevel: 1\n";
        Files.write(tempDir.resolve("tiers").resolve("tier1.yml"),
            schemaOneTier.getBytes(StandardCharsets.UTF_8));

        configService.initialLoad();

        assertTrue(configService.isLoaded(), "Service should be loaded");
        assertFalse(configService.hasValidationErrors(),
            "Should not have validation errors because operator tier1 was skipped with warning");

        assertEquals(7, configService.getAllTiers().size(),
            "Should have all 7 bundled tiers loaded");

        ValidationReport report = configService.getValidationReport();
        assertTrue(report.hasWarnings(), "Should have warnings about schema-version 1");
    }

    @Test
    void initialLoadUsesBundledBaselineWhenOperatorTierYamlIsMalformed() throws Exception {
        setupBundledTiers();

        Files.createDirectories(tempDir.resolve("tiers"));

        String malformedYaml = "schema-version: 2\nid: tier1\nlevel: 1\ninvalid: [unclosed";
        Files.write(tempDir.resolve("tiers").resolve("tier1.yml"),
            malformedYaml.getBytes(StandardCharsets.UTF_8));

        configService.initialLoad();

        assertTrue(configService.isLoaded(), "Service should be loaded");
        assertFalse(configService.hasValidationErrors(),
            "Should not have validation errors because malformed tier was skipped with warning");

        assertEquals(7, configService.getAllTiers().size(),
            "Should have all 7 bundled tiers loaded");

        ValidationReport report = configService.getValidationReport();
        assertTrue(report.hasWarnings(), "Should have warnings about malformed tier");
    }

    @Test
    void invalidBundledTierMakesInitialLoadFailWithExactIssue() throws Exception {
        for (int i = 1; i <= 7; i++) {
            stubBundledResource("tiers/tier" + i + ".yml");
        }

        String invalidTier1Yaml = "schema-version: 2\nid: tier1\nlevel: \"not_a_number\"\n";
        when(mockPlugin.getResource("tiers/tier1.yml"))
            .thenReturn(new ByteArrayInputStream(invalidTier1Yaml.getBytes(StandardCharsets.UTF_8)));

        assertThrows(ConfigurationValidationException.class, configService::initialLoad);

        assertFalse(configService.isLoaded(), "Service should not be loaded after failure");
        assertEquals(0, tierRepository.size(), "Tier repository should be empty after failure");
    }

    @Test
    void validateAsyncReturnsCandidateReportWithoutPublishingSnapshot() throws Exception {
        setupBundledTiers();

        configService.initialLoad();

        ConfigSnapshot snapshotBefore = configService.getCurrentSnapshot();
        int tierCountBefore = configService.getAllTiers().size();
        assertEquals(7, tierCountBefore, "Should have 7 bundled tiers initially");

        Files.createDirectories(tempDir.resolve("tiers"));
        String validationOnlyYaml = "schema-version: 2\nid: validation_only\nlevel: 99\nenabled: true\n";
        Files.write(tempDir.resolve("tiers").resolve("validation-only.yml"),
            validationOnlyYaml.getBytes(StandardCharsets.UTF_8));

        CompletableFuture<ConfigService.ValidationResult> future = configService.validateAsync();
        ConfigService.ValidationResult result = future.getNow(null);

        assertNotNull(result, "Validation result should be available");
        assertEquals(ConfigService.ValidationResult.Status.COMPLETED, result.getStatus(),
            "Validation should complete successfully");

        ConfigSnapshot snapshotAfter = configService.getCurrentSnapshot();
        assertSame(snapshotBefore, snapshotAfter,
            "validateAsync should not change the current snapshot");

        assertEquals(7, configService.getAllTiers().size(),
            "Tier repository size should still be 7 after validation");

        assertFalse(configService.getAllTiers().stream()
            .anyMatch(t -> t.getId().equals("validation_only")),
            "validation_only tier should not be in live repository");
    }

    @SuppressWarnings("unchecked")
    @Test
    void bundledMenuTreeIsDetachedAndPartialOperatorOverlayKeepsRequiredItems() throws Exception {
        setupBundledTiers();

        String operatorMenusYaml =
            "default:\n" +
            "  title: \"<red>Custom Forge\"\n" +
            "  items:\n" +
            "    confirm:\n" +
            "      ready:\n" +
            "        name: \"<green>Custom Forge Button\"\n";
        Files.write(tempDir.resolve("menus.yml"),
            operatorMenusYaml.getBytes(StandardCharsets.UTF_8));

        configService.initialLoad();

        assertTrue(configService.isLoaded(), "Service should be loaded");

        Object menu = configService.getCurrentSnapshot().getAllMenuSettings().get("default");
        assertNotNull(menu, "default menu should exist");

        Map<?, ?> menuMap = assertIsType(Map.class, menu, "menu should be a Map");

        String title = assertIsType(String.class, menuMap.get("title"), "title should be a String");
        assertEquals("<red>Custom Forge", title, "default title should be custom");

        Object items = menuMap.get("items");
        Map<?, ?> itemsMap = assertIsType(Map.class, items, "menu.items should be a Map");

        Object confirm = itemsMap.get("confirm");
        Map<?, ?> confirmMap = assertIsType(Map.class, confirm, "confirm should be a Map");

        Object ready = confirmMap.get("ready");
        Map<?, ?> readyMap = assertIsType(Map.class, ready, "confirm.ready should be a Map");

        String readyName = assertIsType(String.class, readyMap.get("name"), "confirm.ready.name should be a String");
        assertEquals("<green>Custom Forge Button", readyName, "confirm.ready.name should be custom");

        Object readyLore = readyMap.get("lore");
        assertNotNull(readyLore, "confirm.ready.lore should exist in bundled menu");
        assertTrue(readyLore instanceof List, "confirm.ready.lore should be a List, but was: " + (readyLore != null ? readyLore.getClass().getName() : "null"));
        assertFalse(readyLore instanceof Map, "confirm.ready.lore should not be a Map");

        Object blocked = confirmMap.get("blocked");
        assertTrue(blocked instanceof Map, "confirm.blocked should remain bundled Map");
        assertFalse(blocked instanceof List, "confirm.blocked should not be a List");

        Object background = menuMap.get("background");
        assertTrue(background instanceof Map, "background should remain bundled Map");
        assertFalse(background instanceof List, "background should not be a List");

        Map<?, ?> backgroundMap = assertIsType(Map.class, background, "background should be a Map");
        Object bgMaterials = backgroundMap.get("materials");
        assertTrue(bgMaterials instanceof List, "background.materials should be a List");

        assertFalse(hasConfigurationSection(menuMap),
            "Recursive helper should find no ConfigurationSection in bundled menu tree");

        assertThrows(UnsupportedOperationException.class, () -> {
            ((Map) menuMap).put("newKey", "value");
        }, "Nested map mutation should throw UnsupportedOperationException");

        assertThrows(UnsupportedOperationException.class, () -> {
            ((List) readyLore).add("new lore item");
        }, "Nested list mutation should throw UnsupportedOperationException");
    }

    private <T> T assertIsType(Class<T> expectedType, Object actual, String message) {
        assertNotNull(actual, message + " should not be null");
        assertTrue(expectedType.isInstance(actual),
            message + " should be of type " + expectedType.getName() + " but was " + actual.getClass().getName());
        return expectedType.cast(actual);
    }

    private boolean hasConfigurationSection(Object node) {
        if (node instanceof org.bukkit.configuration.ConfigurationSection) {
            return true;
        }
        if (node instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) node;
            for (Object value : map.values()) {
                if (hasConfigurationSection(value)) {
                    return true;
                }
            }
        }
        if (node instanceof List) {
            List<?> list = (List<?>) node;
            for (Object item : list) {
                if (hasConfigurationSection(item)) {
                    return true;
                }
            }
        }
        return false;
    }
}
