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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TierProgressionRegistrationTest {

    private static final String[] CATEGORY_PREFIXES = {"weapon", "armor", "shield", "amulet"};

    @TempDir
    Path tempDir;

    @Test
    void createdTierIsRegisteredInProgressionWithoutRestart() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TierProgressionRegistrationTest"));
        stubAllBundledResources(plugin);
        Files.createDirectories(tempDir.resolve("tiers"));
        Files.write(tempDir.resolve("equipment.yml"),
            readResource("equipment.yml").getBytes(StandardCharsets.UTF_8));

        TierRepository repository = new TierRepository(plugin);
        assertFalse(repository.load().hasErrors());

        TierDefinition created = repository.create("weapon_tier8", 8);

        assertNotNull(created);
        String equipment = new String(Files.readAllBytes(tempDir.resolve("equipment.yml")), StandardCharsets.UTF_8);
        assertTrue(equipment.contains("      - weapon_tier8"), equipment);
        assertEquals(1, countOccurrences(equipment, "- weapon_tier8"));
        assertEquals("weapon_tier8", repository.findForMaterialAndLevel(Material.DIAMOND_SWORD, 8)
            .map(TierDefinition::getId).orElse(null));
        assertEquals(8, repository.maxLevelFor(Material.DIAMOND_SWORD));
    }

    @Test
    void clonedTierRegistersInItsOwnCategoryProgression() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TierProgressionRegistrationTest"));
        stubAllBundledResources(plugin);
        Files.createDirectories(tempDir.resolve("tiers"));
        Files.write(tempDir.resolve("equipment.yml"),
            readResource("equipment.yml").getBytes(StandardCharsets.UTF_8));

        TierRepository repository = new TierRepository(plugin);
        assertFalse(repository.load().hasErrors());

        TierDefinition cloned = repository.clone("weapon_tier1", "armor_tier8", 8);

        assertNotNull(cloned);
        String equipment = new String(Files.readAllBytes(tempDir.resolve("equipment.yml")), StandardCharsets.UTF_8);
        assertTrue(equipment.contains("      - armor_tier8"), equipment);
        assertEquals("armor_tier8", repository.findForMaterialAndLevel(Material.DIAMOND_CHESTPLATE, 8)
            .map(TierDefinition::getId).orElse(null));
        assertEquals(8, repository.maxLevelFor(Material.DIAMOND_CHESTPLATE));
    }

    @Test
    void progressionAppendPreservesCommentsAndAddsOnce() throws Exception {
        Path file = tempDir.resolve("equipment-append.yml");
        String original = "schema-version: 1\n"
            + "# top-level comment\n"
            + "categories:\n"
            + "  weapon:\n"
            + "    # keep this comment\n"
            + "    id: weapon\n"
            + "    progression:\n"
            + "      # first tier\n"
            + "      - weapon_tier1\n"
            + "  armor:\n"
            + "    id: armor\n"
            + "    progression:\n"
            + "      - armor_tier1\n";
        Files.write(file, original.getBytes(StandardCharsets.UTF_8));

        assertTrue(EquipmentProgressionAppender.append(file.toFile(), "weapon", "weapon_tier8"));
        assertTrue(EquipmentProgressionAppender.append(file.toFile(), "weapon", "weapon_tier8"));

        String updated = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(updated.contains("# top-level comment"));
        assertTrue(updated.contains("# keep this comment"));
        assertTrue(updated.contains("# first tier"));
        assertEquals(1, countOccurrences(updated, "- weapon_tier8"));
        assertTrue(updated.contains("      - weapon_tier1\n      - weapon_tier8\n  armor:"), updated);
        assertEquals(1, countOccurrences(updated, "- armor_tier1"));
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
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
