package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TierFolderPolicyTest {

    @TempDir
    Path tempDir;

    private JavaPlugin mockPlugin;
    private TierRepository tierRepository;
    private File dataFolder;
    private File tiersDirectory;

    @BeforeEach
    void setUp() throws IOException {
        dataFolder = tempDir.toFile();
        tiersDirectory = new File(dataFolder, "tiers");

        mockPlugin = mock(JavaPlugin.class);
        when(mockPlugin.getDataFolder()).thenReturn(dataFolder);
    }

    @AfterEach
    void tearDown() {
        tierRepository = null;
    }

    @Test
    void invalidOperatorTierDoesNotDisplaceBundledBaseline() throws Exception {
        for (int i = 1; i <= 7; i++) {
            String resourceName = "tiers/tier" + i + ".yml";
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
            if (stream != null) {
                when(mockPlugin.getResource(resourceName)).thenReturn(stream);
            }
        }

        Files.createDirectories(tiersDirectory.toPath());

        String malformedYaml = "schema-version: 1\nid: tier1\nlevel: 99\nnot: [valid yaml structure";
        File operatorTier1 = new File(tiersDirectory, "tier1.yml");
        Files.write(operatorTier1.toPath(), malformedYaml.getBytes(StandardCharsets.UTF_8));

        tierRepository = new TierRepository(mockPlugin);

        ValidationReport report = tierRepository.load();

        assertFalse(report.hasErrors(), "Should have no errors when operator tier is invalid");
        assertTrue(report.hasWarnings(), "Should have warnings about invalid operator tier");

        boolean hasTier1Warning = report.getWarnings().stream()
            .anyMatch(w -> w.getPath().contains("tier1.yml"));
        assertTrue(hasTier1Warning, "Should have warning about tier1.yml");

        assertEquals(7, tierRepository.size(), "Should still have exactly 7 tiers from bundled baseline");

        for (int i = 1; i <= 7; i++) {
            assertTrue(tierRepository.findByLevel(i).isPresent(),
                "Bundled tier with level " + i + " should still be present");
        }
    }

    @Test
    void operatorTierCanExtendBundledBaseline() throws Exception {
        for (int i = 1; i <= 7; i++) {
            String resourceName = "tiers/tier" + i + ".yml";
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
            if (stream != null) {
                byte[] bytes = toByteArray(stream);
                when(mockPlugin.getResource(resourceName))
                    .thenAnswer(invocation -> new ByteArrayInputStream(bytes.clone()));
            }
        }

        Files.createDirectories(tiersDirectory.toPath());

        String operatorTierYaml = "schema-version: 2\nid: custom_tier\nlevel: 99\nenabled: true\n";
        File operatorTierFile = new File(tiersDirectory, "custom_tier.yml");
        Files.write(operatorTierFile.toPath(), operatorTierYaml.getBytes(StandardCharsets.UTF_8));

        tierRepository = new TierRepository(mockPlugin);

        ValidationReport report = tierRepository.load();

        assertFalse(report.hasErrors(), "Should have no errors: " + report.getErrors());

        assertEquals(8, tierRepository.size(), "Should have 8 tiers (7 bundled + 1 operator)");

        assertTrue(tierRepository.findById("custom_tier").isPresent(),
            "Custom operator tier should be loaded");

        assertTrue(tierRepository.findByLevel(99).isPresent(),
            "Custom operator tier should be accessible by level");

        for (int i = 1; i <= 7; i++) {
            assertTrue(tierRepository.findByLevel(i).isPresent(),
                "Bundled tier with level " + i + " should still be present");
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
    void bootstrapDirectoryCreatesDefaultTiers() throws Exception {
        for (int i = 1; i <= 7; i++) {
            String resourceName = "tiers/tier" + i + ".yml";
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
            if (stream != null) {
                when(mockPlugin.getResource(resourceName)).thenReturn(stream);
            }
        }

        assertFalse(tiersDirectory.exists(), "Tiers directory should not exist initially");

        tierRepository = new TierRepository(mockPlugin);
        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        assertTrue(tiersDirectory.exists(), "Tiers directory should be created");
        assertTrue(tiersDirectory.isDirectory(), "Tiers path should be a directory");

        File[] files = tiersDirectory.listFiles();
        assertNotNull(files, "listFiles should not return null");
        assertEquals(7, files.length, "All seven bundled tiers should be copied");

        for (int i = 1; i <= 7; i++) {
            File tierFile = new File(tiersDirectory, "tier" + i + ".yml");
            assertTrue(tierFile.exists(), "Tier " + i + " file should exist after bootstrap");
        }
    }

    @Test
    void bootstrapDirectoryIsIdempotent() throws Exception {
        for (int i = 1; i <= 7; i++) {
            String resourceName = "tiers/tier" + i + ".yml";
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
            if (stream != null) {
                when(mockPlugin.getResource(resourceName)).thenReturn(stream);
            }
        }

        Files.createDirectories(tiersDirectory.toPath());
        File markerFile = new File(tiersDirectory, "user_modified_tier.yml");
        Files.write(markerFile.toPath(), "schema-version: 2\nid: user_modified\nlevel: 50\n".getBytes(StandardCharsets.UTF_8));

        tierRepository = new TierRepository(mockPlugin);
        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        File[] filesAfterFirstBootstrap = tiersDirectory.listFiles();
        assertNotNull(filesAfterFirstBootstrap);
        int countAfterFirst = filesAfterFirstBootstrap.length;

        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        File[] filesAfterSecondBootstrap = tiersDirectory.listFiles();
        assertNotNull(filesAfterSecondBootstrap);
        int countAfterSecond = filesAfterSecondBootstrap.length;

        assertEquals(countAfterFirst, countAfterSecond,
            "Bootstrap should not add duplicate files on second call");

        assertTrue(markerFile.exists(),
            "User-modified file should still exist after bootstrap");
    }
}
