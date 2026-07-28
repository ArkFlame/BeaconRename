package com.arkflame.flameforge.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    void testAbsentFolderCopiesSeven() throws IOException {
        tierRepository = new TierRepository(mockPlugin);

        for (int i = 1; i <= 7; i++) {
            String resourceName = "tiers/tier" + i + ".yml";
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
            if (stream != null) {
                when(mockPlugin.getResource("tiers/tier" + i + ".yml")).thenReturn(stream);
            }
        }

        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        assertTrue(tiersDirectory.exists(), "Tiers directory should be created");
        assertEquals(7, tiersDirectory.listFiles().length, "All seven bundled tiers should be copied");
    }

    @Test
    void testExistingEmptyFolderCopiesZero() throws IOException {
        Files.createDirectories(tiersDirectory.toPath());

        tierRepository = new TierRepository(mockPlugin);
        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        assertEquals(0, tiersDirectory.listFiles().length, "No files should be copied when directory already exists");
    }

    @Test
    void testExistingPartialFolderDoesNotRestoreDeletedFile() throws IOException {
        Files.createDirectories(tiersDirectory.toPath());

        for (int i = 1; i <= 3; i++) {
            String resourceName = "tiers/tier" + i + ".yml";
            InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
            if (stream != null) {
                when(mockPlugin.getResource("tiers/tier" + i + ".yml")).thenReturn(stream);
            }
        }

        tierRepository = new TierRepository(mockPlugin);
        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        File[] filesAfterFirstBootstrap = tiersDirectory.listFiles();
        assertEquals(3, filesAfterFirstBootstrap.length, "Three tiers should exist after first bootstrap");

        for (File f : filesAfterFirstBootstrap) {
            Files.deleteIfExists(f.toPath());
        }

        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        assertEquals(0, tiersDirectory.listFiles().length, "Deleted files should NOT be restored");
    }

    @Test
    void testPrioritySorting() {
        tierRepository = new TierRepository(mockPlugin);

        tierRepository.create("low_priority", 1);
        tierRepository.create("medium_priority", 5);
        tierRepository.create("high_priority", 10);

        java.util.List<com.arkflame.flameforge.model.TierDefinition> tiers = tierRepository.all();

        assertEquals("high_priority", tiers.get(0).getId(), "Highest priority should be first");
        assertEquals("medium_priority", tiers.get(1).getId(), "Medium priority should be second");
        assertEquals("low_priority", tiers.get(2).getId(), "Lowest priority should be last");
    }

    @Test
    void testDidDirectoryExistBeforeStartup_WhenAbsent() {
        tierRepository = new TierRepository(mockPlugin);
        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        assertTrue(tiersDirectory.exists(), "Directory should exist after bootstrap");
    }

    @Test
    void testDidDirectoryExistBeforeStartup_WhenPreExisted() throws IOException {
        Files.createDirectories(tiersDirectory.toPath());

        tierRepository = new TierRepository(mockPlugin);
        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        assertFalse(tierRepository.didDirectoryExistBeforeStartup(), "Should report directory did not exist before startup");
    }
}
