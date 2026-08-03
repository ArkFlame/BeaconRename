package com.arkflame.flameforge.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void absentFolderCopiesExactlySevenBundledTiersAndMarksNew() throws IOException {
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
    void existingEmptyOrPartialFolderCopiesNothing() throws IOException {
        Files.createDirectories(tiersDirectory.toPath());

        tierRepository = new TierRepository(mockPlugin);
        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        assertEquals(0, tiersDirectory.listFiles().length, "No files should be copied when directory already exists");

        for (File f : tiersDirectory.listFiles()) {
            Files.deleteIfExists(f.toPath());
        }

        tierRepository.bootstrapDefaultsIfDirectoryAbsent();
        assertEquals(0, tiersDirectory.listFiles().length, "Deleted files should NOT be restored after partial bootstrap");
    }

    @Test
    void prioritySortingUsesParsedPriorityThenId() {
        tierRepository = new TierRepository(mockPlugin);

        tierRepository.create("low_priority", 1);
        tierRepository.create("medium_priority", 5);
        tierRepository.create("high_priority", 10);

        java.util.List<com.arkflame.flameforge.model.TierDefinition> tiers = tierRepository.all();

        assertEquals("low_priority", tiers.get(0).getId(), "Lowest priority should be first");
        assertEquals("medium_priority", tiers.get(1).getId(), "Medium priority should be second");
        assertEquals("high_priority", tiers.get(2).getId(), "Highest priority should be last");
    }

    @Test
    void directoryExistenceFlagReflectsPreStartupState() throws IOException {
        tierRepository = new TierRepository(mockPlugin);

        assertFalse(tierRepository.didDirectoryExistBeforeStartup(), "Flag should be false when directory does not exist initially");

        Files.createDirectories(tiersDirectory.toPath());

        tierRepository.bootstrapDefaultsIfDirectoryAbsent();

        assertTrue(tierRepository.didDirectoryExistBeforeStartup(), "Flag should be true when directory existed before startup");
    }
}
