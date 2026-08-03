package com.arkflame.flameforge.resources;

import com.arkflame.flameforge.config.ConfigSnapshot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeatureEvaluationContractTest {

    @Test
    void coreResourceFilesLoadWithRequiredSchemasAndTopLevelSections() {
        YamlConfiguration config = loadConfig("config.yml");
        assertNotNull(config, "config.yml should load");
        assertTrue(config.contains("schema-version"), "config.yml should have schema-version");
        assertEquals(1, config.getInt("schema-version"), "Config schema version should be 1");
        assertTrue(config.contains("enabled"), "config.yml should have enabled");
        assertTrue(config.contains("station-mode"), "config.yml should have station-mode");
        assertTrue(config.contains("audit"), "config.yml should have audit section");
        assertTrue(config.contains("item-groups"), "config.yml should have item-groups section");

        assertTrue(config.contains("announcements"), "config.yml should have announcements section");

        assertTrue(config.contains("holograms"), "config.yml should have holograms section");
        ConfigurationSection holograms = config.getConfigurationSection("holograms");
        assertNotNull(holograms, "holograms section should exist");
        assertTrue(holograms.contains("enabled"), "holograms.enabled should exist");
        assertTrue(holograms.contains("provider-order"), "holograms.provider-order should exist");
        assertTrue(holograms.contains("offset-y"), "holograms.offset-y should exist");
        assertTrue(holograms.contains("transparent-background"), "holograms.transparent-background should exist");
        assertTrue(holograms.contains("lines"), "holograms.lines should exist");
        assertEquals(true, holograms.getBoolean("enabled"), "holograms.enabled should be true");
        assertEquals(1.75, holograms.getDouble("offset-y"), 0.001, "holograms.offset-y should be 1.75");
        assertTrue(holograms.getBoolean("transparent-background"), "holograms.transparent-background should be true");
        assertEquals(Arrays.asList("FancyHolograms", "DecentHolograms"), holograms.getStringList("provider-order"),
            "holograms.provider-order should be [FancyHolograms, DecentHolograms]");
        assertEquals(2, holograms.getStringList("lines").size(), "holograms.lines should have 2 lines");

        InputStream operatorStream = getClass().getClassLoader().getResourceAsStream("operator.yml");
        if (operatorStream != null) {
            YamlConfiguration operatorConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(operatorStream, StandardCharsets.UTF_8));
            assertNotNull(operatorConfig, "operator.yml should load");
            if (operatorConfig.contains("holograms")) {
                ConfigurationSection opHolograms = operatorConfig.getConfigurationSection("holograms");
                if (opHolograms != null) {
                    assertTrue(opHolograms.contains("enabled"), "operator holograms.enabled should exist");
                    assertEquals(Arrays.asList("FancyHolograms", "DecentHolograms"), opHolograms.getStringList("provider-order"),
                        "operator without explicit order inherits bundled defaults");
                }
            }
        }

        YamlConfiguration messages = loadConfig("messages.yml");
        assertNotNull(messages, "messages.yml should load");
        assertTrue(messages.contains("command"), "messages.yml should have command section");
        assertTrue(messages.contains("help"), "messages.yml should have help section");
        assertTrue(messages.contains("open"), "messages.yml should have open section");
        assertTrue(messages.contains("reload"), "messages.yml should have reload section");
        assertTrue(messages.contains("validate"), "messages.yml should have validate section");
        assertTrue(messages.contains("tiers"), "messages.yml should have tiers section");
        assertTrue(messages.contains("tier-info"), "messages.yml should have tier-info section");
        assertTrue(messages.contains("preview"), "messages.yml should have preview section");
        assertTrue(messages.contains("history"), "messages.yml should have history section");
        assertTrue(messages.contains("station"), "messages.yml should have station section");
        assertTrue(messages.contains("forge"), "messages.yml should have forge section");
        assertTrue(messages.contains("cooldown"), "messages.yml should have cooldown section");
        assertTrue(messages.contains("cost"), "messages.yml should have cost section");
        assertTrue(messages.contains("validation"), "messages.yml should have validation section");
        assertTrue(messages.contains("announcements"), "messages.yml should have announcements section");
        assertTrue(messages.contains("menu"), "messages.yml should have menu section");
        assertTrue(messages.contains("delivery"), "messages.yml should have delivery section");

        YamlConfiguration menus = loadConfig("menus.yml");
        assertNotNull(menus, "menus.yml should load");
        assertTrue(menus.contains("schema-version"), "menus.yml should have schema-version");
        assertEquals(2, menus.getInt("schema-version"), "menus.yml schema version should be 2");
        assertTrue(menus.contains("default"), "menus.yml should have default menu profile");

        ConfigurationSection defaultMenu = menus.getConfigurationSection("default");
        assertNotNull(defaultMenu, "default menu should exist");
        assertTrue(defaultMenu.contains("title"), "default menu should have title");
        assertTrue(defaultMenu.contains("size"), "default menu should have size");
        assertEquals(27, defaultMenu.getInt("size"), "default menu size should be 27");
        assertTrue(defaultMenu.contains("slots"), "default menu should have slots section");
        assertTrue(defaultMenu.contains("items"), "default menu should have items section");
        assertTrue(defaultMenu.contains("background"), "default menu should have background section");
        assertTrue(defaultMenu.contains("dynamic-lines"), "default menu should have dynamic-lines section");

        ConfigurationSection slots = defaultMenu.getConfigurationSection("slots");
        assertNotNull(slots, "default menu slots should exist");
        assertEquals(4, slots.getInt("info"), "info slot should be 4");
        assertEquals(13, slots.getInt("input"), "input slot should be 13");
        assertEquals(22, slots.getInt("confirm"), "confirm slot should be 22");
        assertEquals(26, slots.getInt("close"), "close slot should be 26");

        ConfigurationSection background = defaultMenu.getConfigurationSection("background");
        assertNotNull(background, "background section should exist");
        assertTrue(background.contains("materials"), "background should have materials list");
        assertTrue(background.contains("name"), "background should have name");

        ConfigurationSection items = defaultMenu.getConfigurationSection("items");
        assertNotNull(items, "items section should exist");
        assertTrue(items.contains("info"), "items should have info item");
        assertTrue(items.contains("input-empty"), "items should have input-empty item");
        assertTrue(items.contains("confirm-empty"), "items should have confirm-empty item");
        assertTrue(items.contains("confirm-ready"), "items should have confirm-ready item");
        assertTrue(items.contains("close"), "items should have close item");

        ConfigurationSection dynamicLines = defaultMenu.getConfigurationSection("dynamic-lines");
        assertNotNull(dynamicLines, "dynamic-lines section should exist");
        assertTrue(dynamicLines.contains("chance-success"), "dynamic-lines should have chance-success");
        assertTrue(dynamicLines.contains("requirement-xp-met"), "dynamic-lines should have requirement-xp-met");
        assertTrue(dynamicLines.contains("variant-entry"), "dynamic-lines should have variant-entry");
    }

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
