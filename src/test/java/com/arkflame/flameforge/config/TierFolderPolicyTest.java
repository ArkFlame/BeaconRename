package com.arkflame.flameforge.config;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TierFolderPolicyTest {

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

    private void stubBundledTiers(JavaPlugin plugin) {
        for (int level = 1; level <= 7; level++) {
            String path = "tiers/tier" + level + ".yml";
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
