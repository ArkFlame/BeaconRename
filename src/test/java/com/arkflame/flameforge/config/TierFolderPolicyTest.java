package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
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

class TierFolderPolicyTest {

    private static final String[] CATEGORY_PREFIXES = {"weapon", "armor", "shield", "amulet"};

    @TempDir
    Path tempDir;

    @Test
    void tierFolderKeepsBundledBaselineAndAcceptsValidOperatorExtension() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        File dataFolder = tempDir.toFile();
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        stubBundledTiers(plugin);

        Path tiersDirectory = tempDir.resolve("tiers");
        Files.createDirectories(tiersDirectory);
        Path operatorFile = tiersDirectory.resolve("operator.yml");
        Files.write(operatorFile,
            ("schema-version: 2\nid: operator-invalid\nlevel: invalid\n")
                .getBytes(StandardCharsets.UTF_8));

        TierRepository repository = new TierRepository(plugin);
        ValidationReport invalidReport = repository.load();
        assertFalse(invalidReport.hasErrors());
        assertTrue(invalidReport.hasWarnings());
        assertFalse(repository.all().isEmpty());

        Files.write(operatorFile,
            ("schema-version: 2\nid: operator-extension\nlevel: 99\n")
                .getBytes(StandardCharsets.UTF_8));
        ValidationReport validReport = repository.load();

        assertFalse(validReport.hasErrors());
        assertTrue(repository.findById("operator-extension").isPresent());
        assertFalse(repository.all().isEmpty());
    }

    @Test
    void bootstrapCopiesMissingBundledTiersWhenDirectoryAlreadyExists() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubBundledTiers(plugin);

        Files.createDirectories(tempDir.resolve("tiers"));
        TierRepository repository = new TierRepository(plugin);
        repository.bootstrapDefaultsIfDirectoryAbsent();

        for (int level = 1; level <= 7; level++) {
            assertTrue(Files.exists(tempDir.resolve("tiers").resolve("tier" + level + ".yml")));
        }
    }

    @Test
    void duplicateNumericLevelsCoexistBetweenLegacyAndCategoryTiers() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubAllBundledResources(plugin);
        Files.createDirectories(tempDir.resolve("tiers"));

        TierRepository repository = new TierRepository(plugin);
        ValidationReport report = repository.load();

        assertFalse(report.hasErrors());
        assertTrue(repository.findById("tier1").isPresent());
        assertTrue(repository.findById("weapon_tier1").isPresent());
        assertEquals(1, repository.findById("tier1").get().getLevel());
        assertEquals(1, repository.findById("weapon_tier1").get().getLevel());
        assertEquals("weapon_tier1", repository.findForMaterialAndLevel(Material.DIAMOND_SWORD, 1)
            .map(TierDefinition::getId).orElse(null));
    }

    @Test
    void materialAwareProgressionSelectsCategoryTierAtSameNumericLevel() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubAllBundledResources(plugin);
        Files.createDirectories(tempDir.resolve("tiers"));

        TierRepository repository = new TierRepository(plugin);
        ValidationReport report = repository.load();
        assertFalse(report.hasErrors());

        assertEquals("weapon_tier1", repository.findForMaterialAndLevel(Material.DIAMOND_SWORD, 1)
            .map(TierDefinition::getId).orElse(null));
        assertEquals("armor_tier1", repository.findForMaterialAndLevel(Material.DIAMOND_CHESTPLATE, 1)
            .map(TierDefinition::getId).orElse(null));
        assertEquals("weapon_tier2", repository.findExactNext(Material.DIAMOND_SWORD, 1)
            .map(TierDefinition::getId).orElse(null));
        assertEquals("armor_tier2", repository.findExactNext(Material.DIAMOND_CHESTPLATE, 1)
            .map(TierDefinition::getId).orElse(null));
    }

    @Test
    void swordArmorAndWoolResolveToDifferentProgressionTargets() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubAllBundledResources(plugin);
        Files.createDirectories(tempDir.resolve("tiers"));

        TierRepository repository = new TierRepository(plugin);
        ValidationReport report = repository.load();
        assertFalse(report.hasErrors());

        assertEquals("weapon_tier1", repository.findForMaterialAndLevel(Material.DIAMOND_SWORD, 1)
            .map(TierDefinition::getId).orElse(null));
        assertEquals("armor_tier1", repository.findForMaterialAndLevel(Material.DIAMOND_CHESTPLATE, 1)
            .map(TierDefinition::getId).orElse(null));
        assertEquals("amulet_tier1", repository.findForMaterialAndLevel(Material.WOOL, 1)
            .map(TierDefinition::getId).orElse(null));
        assertEquals(7, repository.maxLevelFor(Material.DIAMOND_SWORD));
        assertEquals(7, repository.maxLevelFor(Material.WOOL));
    }

    @Test
    void incompleteConfiguredProgressionIsValidationErrorAndIsNotPublished() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubAllBundledResources(plugin);
        Files.createDirectories(tempDir.resolve("tiers"));

        Files.write(tempDir.resolve("equipment.yml"),
            ("schema-version: 1\n" +
             "categories:\n" +
             "  weapon:\n" +
             "    id: weapon\n" +
             "    fallback: false\n" +
             "    materials:\n" +
             "      - DIAMOND_SWORD\n" +
             "    progression:\n" +
             "      - weapon_tier1\n")
                .getBytes(StandardCharsets.UTF_8));

        TierRepository repository = new TierRepository(plugin);
        ValidationReport report = repository.load();

        assertTrue(report.hasErrors());
        assertFalse(repository.findById("weapon_tier1").isPresent());
    }

    @Test
    void invalidOperatorTierKeepsValidBundledCategoryTier() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubAllBundledResources(plugin);

        Path tiersDirectory = tempDir.resolve("tiers");
        Files.createDirectories(tiersDirectory);
        Files.write(tiersDirectory.resolve("broken.yml"),
            ("schema-version: 2\nid: broken\nlevel: not-a-number\n")
                .getBytes(StandardCharsets.UTF_8));

        TierRepository repository = new TierRepository(plugin);
        ValidationReport report = repository.load();

        assertFalse(report.hasErrors());
        assertTrue(report.hasWarnings());
        assertTrue(repository.findById("weapon_tier1").isPresent());
        assertEquals("weapon_tier1", repository.findForMaterialAndLevel(Material.DIAMOND_SWORD, 1)
            .map(TierDefinition::getId).orElse(null));
    }

    private void stubAllBundledResources(JavaPlugin plugin) {
        stubBundledResource(plugin, "equipment.yml");
        stubBundledTiers(plugin);
        for (int level = 1; level <= 7; level++) {
            for (String category : CATEGORY_PREFIXES) {
                stubBundledResource(plugin, "tiers/" + category + "_tier" + level + ".yml");
            }
        }
    }

    private void stubBundledTiers(JavaPlugin plugin) {
        for (int level = 1; level <= 7; level++) {
            stubBundledResource(plugin, "tiers/tier" + level + ".yml");
        }
    }

    private void stubBundledResource(JavaPlugin plugin, String path) {
        InputStream source = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(source);
        try {
            byte[] bytes = readBytes(source);
            when(plugin.getResource(path))
                .thenAnswer(invocation -> new ByteArrayInputStream(bytes.clone()));
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
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
