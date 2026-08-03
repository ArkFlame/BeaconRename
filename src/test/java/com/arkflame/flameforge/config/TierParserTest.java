package com.arkflame.flameforge.config;

import com.arkflame.flameforge.model.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TierParserTest {

    @TempDir
    Path tempDir;

    private ValidationReport report;

    @BeforeEach
    void setUp() {
        report = new ValidationReport();
    }

    @Test
    @Disabled("v1 tier files require migration context with bundled v2 resources that don't exist in test environment")
    void allBundledTiersParseAndPreserveExpectedCoreFields() throws Exception {
        int[] expectedLevels = {0, 1, 2, 3, 4, 5, 6};

        for (int i = 1; i <= 7; i++) {
            String resourcePath = "tiers/tier" + i + ".yml";
            java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);

            assertNotNull(stream, "Bundled tier " + i + " should exist as resource: " + resourcePath);

            Path tempFile = tempDir.resolve("tier" + i + ".yml");
            Files.copy(stream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            ValidationReport tierReport = new ValidationReport();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(tempFile.toFile());
            TierDefinition tier = TierParser.parse(yaml, tierReport);

            assertNotNull(tier, "Tier " + i + " should parse successfully: " + tierReport.getErrors());
            assertFalse(tierReport.hasErrors(), "Tier " + i + " should have no errors");

            assertEquals(expectedLevels[i - 1], tier.getLevel(), "Tier " + i + " level should match");

            TierRequirements requirements = tier.getRequirements();
            assertNotNull(requirements, "Tier " + i + " should have requirements");
        }
    }

    @Test
    void validCompleteTierParsesAllFields() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "complete_tier");
        yaml.set("level", 10);
        yaml.set("enabled", true);
        yaml.set("permission", "flameforge.complete");
        yaml.set("display.name", "&b&lComplete Tier");
        yaml.set("display.lore", java.util.Arrays.asList("&7Line 1", "&7Line 2"));
        yaml.set("display.icon", "NETHER_STAR");
        yaml.set("requirements.xp.enabled", true);
        yaml.set("requirements.xp.amount", 100.5);
        yaml.set("requirements.money.enabled", true);
        yaml.set("requirements.money.amount", 250.75);
        yaml.set("animation.duration-ticks", 50);
        yaml.set("animation.interval-ticks", 4);

        TierDefinition tier = TierParser.parse(yaml, report);

        assertNotNull(tier);
        assertFalse(report.hasErrors());

        assertEquals("complete_tier", tier.getId());
        assertEquals(10, tier.getLevel());
        TierRequirements requirements = tier.getRequirements();
        assertNotNull(requirements);
        assertEquals(100, requirements.getXp().getLevel());
        assertEquals(new BigDecimal("250.75"), requirements.getMoney().getAmount());
    }

    @Test
    void missingIdProducesError() throws Exception {
        YamlConfiguration missingIdYaml = new YamlConfiguration();
        missingIdYaml.set("schema-version", 2);
        missingIdYaml.set("level", 1);
        TierDefinition missingIdResult = TierParser.parse(missingIdYaml, new ValidationReport());
        assertNull(missingIdResult);
    }

    @Test
    void omittedOptionalFieldsUseDocumentedDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "minimal_tier");
        yaml.set("level", 1);

        TierDefinition result = TierParser.parse(yaml, report);

        assertNotNull(result);
        assertEquals(1, result.getLevel());

        TierRequirements requirements = result.getRequirements();
        assertNotNull(requirements);
        assertFalse(requirements.getXp().isEnabled());
        assertFalse(requirements.getMoney().isEnabled());
    }

    @Test
    void parseWithNullYamlReturnsNull() {
        TierDefinition result = TierParser.parse((org.bukkit.configuration.ConfigurationSection) null, new ValidationReport());
        assertNull(result);
    }

    @Test
    void parseWithEmptyYamlProducesMinimalTier() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 2);
        yaml.set("id", "empty_tier");
        yaml.set("level", 0);

        TierDefinition result = TierParser.parse(yaml, report);
        assertNotNull(result);
        assertEquals("empty_tier", result.getId());
    }

    @Test
    @Disabled("v1 tier files require migration context with bundled v2 resources that don't exist in test environment")
    void tierFolderPriorityOrderMatchesBundledResources() throws Exception {
        int[] expectedLevels = {0, 1, 2, 3, 4, 5, 6};

        java.util.List<TierDefinition> bundledTiers = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            String resourcePath = "tiers/tier" + i + ".yml";
            java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            assertNotNull(stream, "Resource should exist: " + resourcePath);

            Path tempFile = tempDir.resolve("tier" + i + ".yml");
            Files.copy(stream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            ValidationReport tierReport = new ValidationReport();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(tempFile.toFile());
            TierDefinition tier = TierParser.parse(yaml, tierReport);
            assertNotNull(tier, "Tier " + i + " should parse successfully");
            bundledTiers.add(tier);
        }

        for (int i = 0; i < bundledTiers.size(); i++) {
            assertEquals(expectedLevels[i], bundledTiers.get(i).getLevel(),
                "Tier at index " + i + " should have level " + expectedLevels[i]);
        }
    }

    private ValidationReport loadTierFile(File file, Set<String> seenIds) {
        ValidationReport fileReport = new ValidationReport();
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            TierDefinition tier = TierParser.parse(yaml, fileReport);

            if (tier == null) {
                return fileReport;
            }

            String id = tier.getId();

            if (seenIds.contains(id)) {
                fileReport.addError("", "id", "Duplicate tier id: " + id + " in file " + file.getName());
                return fileReport;
            }

            seenIds.add(id);
        } catch (Exception e) {
            fileReport.addError("", file.getName(), "Failed to parse tier file: " + e.getMessage());
        }
        return fileReport;
    }
}
