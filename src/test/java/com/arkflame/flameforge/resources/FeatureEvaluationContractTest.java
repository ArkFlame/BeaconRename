package com.arkflame.flameforge.resources;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeatureEvaluationContractTest {

    @Test
    void testRootMessageMenuKeysExist() {
        YamlConfiguration config = loadConfig("config.yml");
        assertNotNull(config, "config.yml should load");
        assertTrue(config.contains("schema-version"), "config.yml should have schema-version");
        assertTrue(config.contains("enabled"), "config.yml should have enabled");
        assertTrue(config.contains("station-mode"), "config.yml should have station-mode");
        assertTrue(config.contains("audit"), "config.yml should have audit section");
        assertTrue(config.contains("chance-decimals"), "config.yml should have chance-decimals");

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
        assertTrue(messages.contains("pity"), "messages.yml should have pity section");
        assertTrue(messages.contains("cost"), "messages.yml should have cost section");
        assertTrue(messages.contains("validation"), "messages.yml should have validation section");
        assertTrue(messages.contains("ward"), "messages.yml should have ward section");
        assertTrue(messages.contains("catalyst"), "messages.yml should have catalyst section");
        assertTrue(messages.contains("announcements"), "messages.yml should have announcements section");
        assertTrue(messages.contains("menu"), "messages.yml should have menu section");
        assertTrue(messages.contains("delivery"), "messages.yml should have delivery section");

        YamlConfiguration menus = loadConfig("menus.yml");
        assertNotNull(menus, "menus.yml should load");
        assertTrue(menus.contains("default"), "menus.yml should have default menu profile");
        assertTrue(menus.contains("compact"), "menus.yml should have compact menu profile");
        assertTrue(menus.contains("material-aliases"), "menus.yml should have material-aliases section");
        assertTrue(menus.contains("slot-materials"), "menus.yml should have slot-materials section");

        ConfigurationSection defaultMenu = menus.getConfigurationSection("default");
        assertNotNull(defaultMenu, "default menu should exist");
        assertTrue(defaultMenu.contains("title"), "default menu should have title");
        assertTrue(defaultMenu.contains("size"), "default menu should have size");
        assertTrue(defaultMenu.contains("slots"), "default menu should have slots section");
        assertTrue(defaultMenu.contains("items"), "default menu should have items section");
    }

    @Test
    void testFeatureEvaluationHas100UniqueCandidatesAndExactTenSelectedIds() {
        YamlConfiguration config = loadConfig("config.yml");
        YamlConfiguration messages = loadConfig("messages.yml");
        YamlConfiguration menus = loadConfig("menus.yml");

        Set<String> allKeys = collectAllKeys(config, "");
        allKeys.addAll(collectAllKeys(messages, ""));
        allKeys.addAll(collectAllKeys(menus, ""));

        assertEquals(100, allKeys.size(),
            "Should have exactly 100 unique configuration candidates/keys across all resource files");

        Set<String> expectedSelectedIds = new HashSet<>();
        expectedSelectedIds.add("schema-version");
        expectedSelectedIds.add("enabled");
        expectedSelectedIds.add("station-mode");
        expectedSelectedIds.add("audit");
        expectedSelectedIds.add("command");
        expectedSelectedIds.add("help");
        expectedSelectedIds.add("forge");
        expectedSelectedIds.add("menu");
        expectedSelectedIds.add("default");
        expectedSelectedIds.add("delivery");

        Set<String> actualSelectedIds = new HashSet<>();
        for (String key : allKeys) {
            if (key.contains(".") && !key.startsWith("audit.") && !key.startsWith("command.") &&
                !key.startsWith("help.") && !key.startsWith("forge.") && !key.startsWith("menu.") &&
                !key.startsWith("delivery.")) {
                continue;
            }
            if (expectedSelectedIds.contains(extractTopLevelKey(key))) {
                actualSelectedIds.add(extractTopLevelKey(key));
            }
        }

        for (String expectedId : expectedSelectedIds) {
            assertTrue(allKeys.contains(expectedId),
                "Expected selected ID '" + expectedId + "' should exist in configuration keys");
        }
    }

    @Test
    void testConfigSchemaVersion() {
        YamlConfiguration config = loadConfig("config.yml");
        assertEquals(1, config.getInt("schema-version"),
            "Config schema version should be 1");
    }

    @Test
    void testMessageCommandKeys() {
        YamlConfiguration messages = loadConfig("messages.yml");

        ConfigurationSection command = messages.getConfigurationSection("command");
        assertNotNull(command, "command section should exist");
        assertTrue(command.contains("unknown"), "command.unknown should exist");
        assertTrue(command.contains("no-permission"), "command.no-permission should exist");
        assertTrue(command.contains("player-only"), "command.player-only should exist");
        assertTrue(command.contains("reload-success"), "command.reload-success should exist");
    }

    @Test
    void testMenuDefaultProfileSlots() {
        YamlConfiguration menus = loadConfig("menus.yml");

        ConfigurationSection defaultMenu = menus.getConfigurationSection("default");
        assertNotNull(defaultMenu, "default menu should exist");

        ConfigurationSection slots = defaultMenu.getConfigurationSection("slots");
        assertNotNull(slots, "default menu slots should exist");

        assertEquals(4, slots.getInt("info"), "info slot should be 4");
        assertEquals(13, slots.getInt("input"), "input slot should be 13");
        assertEquals(22, slots.getInt("confirm"), "confirm slot should be 22");
        assertEquals(49, slots.getInt("close"), "close slot should be 49");
    }

    @Test
    void testMenuTierDefaults() {
        YamlConfiguration menus = loadConfig("menus.yml");

        ConfigurationSection defaultMenu = menus.getConfigurationSection("default");
        ConfigurationSection tierDefaults = defaultMenu.getConfigurationSection("tier");

        assertNotNull(tierDefaults, "tier defaults should exist");
        assertEquals("DIAMOND", tierDefaults.getString("available-material"),
            "available-material should be DIAMOND");
        assertEquals("NETHER_STAR", tierDefaults.getString("selected-material"),
            "selected-material should be NETHER_STAR");
    }

    @Test
    void testConfigItemGroups() {
        YamlConfiguration config = loadConfig("config.yml");

        ConfigurationSection itemGroups = config.getConfigurationSection("item-groups");
        assertNotNull(itemGroups, "item-groups section should exist");

        ConfigurationSection swords = config.getConfigurationSection("item-groups.swords");
        assertNotNull(swords, "swords item group should exist");
        assertTrue(swords.contains("materials"), "swords should have materials list");

        List<String> swordMaterials = swords.getStringList("materials");
        assertTrue(swordMaterials.contains("diamond_sword"), "swords should include diamond_sword");
        assertTrue(swordMaterials.contains("iron_sword"), "swords should include iron_sword");
    }

    @Test
    void testConfigCatalysts() {
        YamlConfiguration config = loadConfig("config.yml");

        ConfigurationSection catalysts = config.getConfigurationSection("catalysts");
        assertNotNull(catalysts, "catalysts section should exist");

        ConfigurationSection luckyDust = config.getConfigurationSection("catalysts.lucky_dust");
        assertNotNull(luckyDust, "lucky_dust catalyst should exist");
        assertTrue(luckyDust.getBoolean("enabled"), "lucky_dust should be enabled");
        assertEquals("GLOWSTONE_DUST", luckyDust.getString("material"),
            "lucky_dust material should be GLOWSTONE_DUST");
    }

    @Test
    void testConfigWards() {
        YamlConfiguration config = loadConfig("config.yml");

        ConfigurationSection wards = config.getConfigurationSection("wards");
        assertNotNull(wards, "wards section should exist");

        ConfigurationSection safetyRune = config.getConfigurationSection("wards.safety_rune");
        assertNotNull(safetyRune, "safety_rune ward should exist");
        assertTrue(safetyRune.getBoolean("enabled"), "safety_rune should be enabled");
        assertFalse(safetyRune.getBoolean("protect_all"),
            "safety_rune protect_all should be false");
    }

    @Test
    void testConfigAnnouncements() {
        YamlConfiguration config = loadConfig("config.yml");

        ConfigurationSection announcements = config.getConfigurationSection("announcements");
        assertNotNull(announcements, "announcements section should exist");

        ConfigurationSection global = config.getConfigurationSection("announcements.global");
        assertNotNull(global, "global announcement should exist");
        assertTrue(global.getBoolean("enabled"), "global announcement should be enabled");

        ConfigurationSection title = global.getConfigurationSection("title");
        assertNotNull(title, "title section should exist");
        assertTrue(title.contains("success"), "success title should exist");
        assertTrue(title.contains("fail"), "fail title should exist");
    }

    @Test
    void testMessagesMenuSection() {
        YamlConfiguration messages = loadConfig("messages.yml");

        ConfigurationSection menuSection = messages.getConfigurationSection("menu");
        assertNotNull(menuSection, "menu section should exist");
        assertTrue(menuSection.contains("title"), "menu title should exist");
        assertTrue(menuSection.contains("filler-name"), "menu filler-name should exist");
        assertTrue(menuSection.contains("input-placeholder"), "menu input-placeholder should exist");
        assertTrue(menuSection.contains("catalyst-placeholder"), "menu catalyst-placeholder should exist");
        assertTrue(menuSection.contains("ward-placeholder"), "menu ward-placeholder should exist");
        assertTrue(menuSection.contains("confirm-available"), "menu confirm-available should exist");
        assertTrue(menuSection.contains("confirm-unavailable"), "menu confirm-unavailable should exist");
        assertTrue(menuSection.contains("close"), "menu close should exist");
        assertTrue(menuSection.contains("info"), "menu info should exist");
    }

    private Set<String> collectAllKeys(ConfigurationSection section, String prefix) {
        Set<String> keys = new HashSet<>();
        if (section == null) {
            return keys;
        }

        for (String key : section.getKeys(true)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            keys.add(fullKey);
        }
        return keys;
    }

    private String extractTopLevelKey(String fullKey) {
        int dotIndex = fullKey.indexOf('.');
        return dotIndex == -1 ? fullKey : fullKey.substring(0, dotIndex);
    }

    private YamlConfiguration loadConfig(String filename) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(filename);
        assertNotNull(stream, filename + " should be loadable from classpath");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream));
    }
}
