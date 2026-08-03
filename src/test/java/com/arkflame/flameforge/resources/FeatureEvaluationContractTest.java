package com.arkflame.flameforge.resources;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FeatureEvaluationContractTest {

    @Test
    void defaultMenuAndConfiguredItemFeaturesContainValidRequiredValues() {
        YamlConfiguration config = loadConfig("config.yml");
        YamlConfiguration menus = loadConfig("menus.yml");

        ConfigurationSection itemGroups = config.getConfigurationSection("item-groups");
        assertNotNull(itemGroups, "item-groups section should exist");

        ConfigurationSection swords = config.getConfigurationSection("item-groups.swords");
        assertNotNull(swords, "swords item group should exist");
        assertTrue(swords.contains("materials"), "swords should have materials list");
        java.util.List<String> swordMaterials = swords.getStringList("materials");
        assertTrue(swordMaterials.contains("diamond_sword"), "swords should include diamond_sword");
        assertTrue(swordMaterials.contains("iron_sword"), "swords should include iron_sword");

        ConfigurationSection announcements = config.getConfigurationSection("announcements");
        assertNotNull(announcements, "announcements section should exist");

        ConfigurationSection global = config.getConfigurationSection("announcements.global");
        assertNotNull(global, "global announcement should exist");
        assertTrue(global.getBoolean("enabled"), "global announcement should be enabled");

        ConfigurationSection title = global.getConfigurationSection("title");
        assertNotNull(title, "title section should exist");
        assertTrue(title.contains("success"), "success title should exist");
        assertTrue(title.contains("fail"), "fail title should exist");

        ConfigurationSection menuSection = menus.getConfigurationSection("default");
        assertNotNull(menuSection, "default menu section should exist");
        assertTrue(menuSection.contains("title"), "menu title should exist");
        assertTrue(menuSection.contains("size"), "menu size should exist");
        assertTrue(menuSection.contains("slots"), "menu slots should exist");
        assertTrue(menuSection.contains("items"), "menu items should exist");

        InputStream operatorStream = getClass().getClassLoader().getResourceAsStream("operator.yml");
        if (operatorStream != null) {
            YamlConfiguration operatorConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(operatorStream, StandardCharsets.UTF_8));
            assertNotNull(operatorConfig, "operator.yml should load from classpath");
            if (operatorConfig.contains("holograms")) {
                ConfigurationSection opHolograms = operatorConfig.getConfigurationSection("holograms");
                assertNotNull(opHolograms, "operator holograms section should exist");
                if (opHolograms.contains("enabled")) {
                    assertEquals(false, opHolograms.getBoolean("enabled"),
                        "explicit false override should work");
                }
                if (opHolograms.contains("provider-order")) {
                    java.util.List<String> customOrder = opHolograms.getStringList("provider-order");
                    assertFalse(customOrder.isEmpty(), "explicit order should not be empty");
                }
                if (opHolograms.contains("lines")) {
                    java.util.List<String> customLines = opHolograms.getStringList("lines");
                    assertFalse(customLines.isEmpty(), "explicit lines should not be empty");
                }
            }
        }
    }

    private YamlConfiguration loadConfig(String filename) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(filename);
        assertNotNull(stream, filename + " should be loadable from classpath");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
