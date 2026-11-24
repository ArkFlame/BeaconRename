package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TierProgressionExtensionTest {

    private static final String[] CATEGORY_PREFIXES = {"weapon", "armor", "shield", "amulet"};

    @TempDir
    Path tempDir;

    @Test
    void extendedProgressionLoadsEighthTier() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubAllBundledResources(plugin);

        String equipment = readResource("equipment.yml")
            .replace("      - weapon_tier7\n", "      - weapon_tier7\n      - weapon_tier8\n");
        assertTrue(equipment.contains("weapon_tier8"));
        Files.write(tempDir.resolve("equipment.yml"), equipment.getBytes(StandardCharsets.UTF_8));

        Path tiersDirectory = tempDir.resolve("tiers");
        Files.createDirectories(tiersDirectory);
        String tier8 = readResource("tiers/weapon_tier7.yml")
            .replace("id: weapon_tier7", "id: weapon_tier8")
            .replace("level: 7", "level: 8");
        assertTrue(tier8.contains("id: weapon_tier8"));
        Files.write(tiersDirectory.resolve("weapon_tier8.yml"), tier8.getBytes(StandardCharsets.UTF_8));

        TierRepository repository = new TierRepository(plugin);
        ValidationReport report = repository.load();

        assertFalse(report.hasErrors(), () -> "Unexpected errors: " + report.getErrors());
        assertTrue(repository.findById("weapon_tier8").isPresent());
        assertEquals(8, repository.maxLevelFor(Material.DIAMOND_SWORD));
        assertEquals("weapon_tier8", repository.findForMaterialAndLevel(Material.DIAMOND_SWORD, 8)
            .map(TierDefinition::getId).orElse(null));
    }

    private void stubAllBundledResources(JavaPlugin plugin) {
        stubBundledResource(plugin, "equipment.yml");
        for (int level = 1; level <= 7; level++) {
            stubBundledResource(plugin, "tiers/tier" + level + ".yml");
            for (String category : CATEGORY_PREFIXES) {
                stubBundledResource(plugin, "tiers/" + category + "_tier" + level + ".yml");
            }
        }
    }

    private void stubBundledResource(JavaPlugin plugin, String path) {
        byte[] bytes = readResource(path).getBytes(StandardCharsets.UTF_8);
        when(plugin.getResource(path)).thenAnswer(invocation -> new ByteArrayInputStream(bytes.clone()));
    }

    private String readResource(String path) {
        try (InputStream source = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(source, "Missing bundled resource: " + path);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[256];
                int read;
                while ((read = source.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
