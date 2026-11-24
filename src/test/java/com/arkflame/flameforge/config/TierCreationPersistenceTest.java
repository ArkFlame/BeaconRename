package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;
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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TierCreationPersistenceTest {

    private static final String[] CATEGORY_PREFIXES = {"weapon", "armor", "shield", "amulet"};

    @TempDir
    Path tempDir;

    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TierCreationPersistenceTest"));
        stubAllBundledResources();
    }

    @Test
    void createdTierIsWrittenAndReloads() throws Exception {
        Files.createDirectories(tempDir.resolve("tiers"));
        TierRepository repository = new TierRepository(plugin);
        assertFalse(repository.load().hasErrors());

        TierDefinition created = repository.create("weapon_tier8", 8);

        assertNotNull(created);
        Path file = tempDir.resolve("tiers").resolve("weapon_tier8.yml");
        assertTrue(Files.exists(file));

        TierRepository reloaded = new TierRepository(plugin);
        ValidationReport report = reloaded.load();

        assertFalse(report.hasErrors(), () -> "Unexpected errors: " + report.getErrors());
        TierDefinition loaded = reloaded.findById("weapon_tier8").orElse(null);
        assertNotNull(loaded);
        assertEquals("weapon_tier8", loaded.getId());
        assertEquals(8, loaded.getLevel());
    }

    @Test
    void clonedTierRoundTripsKeyData() throws Exception {
        Files.createDirectories(tempDir.resolve("tiers"));
        TierRepository repository = new TierRepository(plugin);
        assertFalse(repository.load().hasErrors());
        TierDefinition source = repository.findById("weapon_tier1").orElse(null);
        assertNotNull(source);

        TierDefinition cloned = repository.clone("weapon_tier1", "weapon_tier8", 8);

        assertNotNull(cloned);
        assertTrue(Files.exists(tempDir.resolve("tiers").resolve("weapon_tier8.yml")));

        TierRepository reloaded = new TierRepository(plugin);
        assertFalse(reloaded.load().hasErrors());
        TierDefinition loaded = reloaded.findById("weapon_tier8").orElse(null);
        assertNotNull(loaded);
        assertEquals(8, loaded.getLevel());
        assertEquals(source.getDisplay().getName(), loaded.getDisplay().getName());
        assertEquals(source.getChances().getSuccessPercent(), loaded.getChances().getSuccessPercent());
        assertFalse(loaded.getVariants().isEmpty());
        assertEquals(source.getVariants().size(), loaded.getVariants().size());
    }

    @Test
    void blankUnsafeAndDuplicateIdsAreRejected() throws Exception {
        Files.createDirectories(tempDir.resolve("tiers"));
        TierRepository repository = new TierRepository(plugin);
        assertFalse(repository.load().hasErrors());

        assertNull(repository.create("   ", 8));
        assertNull(repository.create("../evil", 8));
        assertNull(repository.create("a/b", 8));

        assertFalse(Files.exists(tempDir.resolve("evil.yml")));
        assertFalse(Files.exists(tempDir.resolve("a")));
        try (java.util.stream.Stream<Path> files = Files.list(tempDir.resolve("tiers"))) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains("evil")));
        }

        assertNull(repository.create("WEAPON_TIER1", 1));
        assertNotNull(repository.create("weapon_tier8", 8));
        assertNull(repository.create("weapon_tier8", 8));
        try (java.util.stream.Stream<Path> files = Files.list(tempDir.resolve("tiers"))) {
            assertEquals(1, files.filter(path -> path.getFileName().toString().equalsIgnoreCase("weapon_tier8.yml")).count());
        }
    }

    private void stubAllBundledResources() {
        stubBundledResource("equipment.yml");
        for (int level = 1; level <= 7; level++) {
            stubBundledResource("tiers/tier" + level + ".yml");
            for (String category : CATEGORY_PREFIXES) {
                stubBundledResource("tiers/" + category + "_tier" + level + ".yml");
            }
        }
    }

    private void stubBundledResource(String path) {
        InputStream source = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(source, "Missing bundled resource: " + path);
        byte[] bytes;
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
        when(plugin.getResource(path)).thenAnswer(invocation -> new ByteArrayInputStream(bytes.clone()));
    }
}
