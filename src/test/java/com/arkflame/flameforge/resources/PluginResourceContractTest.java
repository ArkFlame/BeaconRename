package com.arkflame.flameforge.resources;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginResourceContractTest {

    @Test
    void testPluginMainExact() {
        PluginDescriptionFile pdf = loadPluginDescription();
        assertEquals("com.arkflame.flameforge.FlameForgePlugin", pdf.getMain(),
            "Plugin main class should be exact");
    }

    @Test
    void testPluginNameExact() {
        PluginDescriptionFile pdf = loadPluginDescription();
        assertEquals("FlameForge", pdf.getName(),
            "Plugin name should be exact");
    }

    @Test
    void testPluginVersion() {
        PluginDescriptionFile pdf = loadPluginDescription();
        assertNotNull(pdf.getVersion(), "Plugin version should not be null");
        assertEquals("1.0.0", pdf.getVersion(), "Plugin version should be 1.0.0");
    }

    @Test
    void testPluginAuthor() {
        PluginDescriptionFile pdf = loadPluginDescription();
        List<String> authors = pdf.getAuthors();
        assertNotNull(authors, "Authors should not be null");
        assertTrue(authors.contains("ArkFlame Studios"),
            "Author should be ArkFlame Studios");
    }

    @Test
    void testSoftDependsExact() {
        PluginDescriptionFile pdf = loadPluginDescription();
        List<String> softDeps = pdf.getSoftDepend();

        assertNotNull(softDeps, "Soft depends should not be null");
        assertEquals(2, softDeps.size(), "Should have exactly 2 soft depends");
        assertTrue(softDeps.contains("Vault"), "Should soft-depend on Vault");
        assertTrue(softDeps.contains("SMPWeapons"), "Should soft-depend on SMPWeapons");
    }

    @Test
    void testCommandsExact() {
        PluginDescriptionFile pdf = loadPluginDescription();
        Map<String, Map<String, Object>> commands = pdf.getCommands();

        assertNotNull(commands, "Commands should not be null");
        assertTrue(commands.containsKey("flameforge"), "Should have flameforge command");

        Map<String, Object> flameforgeCmd = commands.get("flameforge");
        assertNotNull(flameforgeCmd, "flameforge command should not be null");

        assertEquals("The root command for FlameForge", flameforgeCmd.get("description"),
            "Command description should match");

        Object aliasesObj = flameforgeCmd.get("aliases");
        assertNotNull(aliasesObj, "Command aliases should not be null");

        if (aliasesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> aliases = (List<String>) aliasesObj;
            assertTrue(aliases.contains("forge"), "Should have 'forge' alias");
            assertTrue(aliases.contains("ff"), "Should have 'ff' alias");
        } else if (aliasesObj instanceof String) {
            String aliases = (String) aliasesObj;
            assertTrue(aliases.contains("forge") || aliases.contains("ff"),
                "Should have aliases string containing 'forge' or 'ff'");
        }

        assertEquals("/<command> [args]", flameforgeCmd.get("usage"),
            "Command usage should match");
        assertEquals("flameforge.use", flameforgeCmd.get("permission"),
            "Command permission should match");
    }

    @Test
    void testPermissionsExist() {
        PluginDescriptionFile pdf = loadPluginDescription();

        List<org.bukkit.permissions.Permission> perms = pdf.getPermissions();
        assertNotNull(perms, "Permissions should not be null");
        assertFalse(perms.isEmpty(), "Permissions should not be empty");

        boolean hasUsePerm = false;
        boolean hasAdminPerm = false;

        for (org.bukkit.permissions.Permission perm : perms) {
            if ("flameforge.use".equals(perm.getName())) {
                hasUsePerm = true;
            }
            if ("flameforge.admin".equals(perm.getName())) {
                hasAdminPerm = true;
            }
        }

        assertTrue(hasUsePerm, "Should have flameforge.use permission");
        assertTrue(hasAdminPerm, "Should have flameforge.admin permission");
    }

    @Test
    void testPluginDescriptorLoadsFromClasspath() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream, "plugin.yml should be loadable from classpath");

        try {
            PluginDescriptionFile pdf = new PluginDescriptionFile(stream);
            assertNotNull(pdf.getMain(), "Main class should be parsed from plugin.yml");
            assertNotNull(pdf.getName(), "Name should be parsed from plugin.yml");
        } catch (InvalidDescriptionException e) {
            throw new AssertionError("plugin.yml should be valid", e);
        }
    }

    @Test
    void testRawPluginYamlContent() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream, "plugin.yml should be loadable from classpath");

        org.bukkit.configuration.file.YamlConfiguration yaml =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream));

        assertEquals("FlameForge", yaml.getString("name"));
        assertEquals("com.arkflame.flameforge.FlameForgePlugin", yaml.getString("main"));
        assertEquals("1.13", yaml.getString("api-version"));
        assertEquals(true, yaml.getBoolean("folia-supported"));

        List<String> softdepends = yaml.getStringList("softdepend");
        assertTrue(softdepends.contains("Vault"));
        assertTrue(softdepends.contains("SMPWeapons"));

        Map<String, Object> commands = yaml.getConfigurationSection("commands").getValues(false);
        assertTrue(commands.containsKey("flameforge"));

        Map<String, Object> perms = yaml.getConfigurationSection("permissions").getValues(false);
        assertTrue(perms.containsKey("flameforge.use"));
        assertTrue(perms.containsKey("flameforge.command.help"));
        assertTrue(perms.containsKey("flameforge.command.open"));
        assertTrue(perms.containsKey("flameforge.command.reload"));
        assertTrue(perms.containsKey("flameforge.command.validate"));
        assertTrue(perms.containsKey("flameforge.command.tiers"));
        assertTrue(perms.containsKey("flameforge.command.tier.info"));
        assertTrue(perms.containsKey("flameforge.command.preview"));
        assertTrue(perms.containsKey("flameforge.command.history"));
        assertTrue(perms.containsKey("flameforge.command.history.others"));
        assertTrue(perms.containsKey("flameforge.command.station.add"));
        assertTrue(perms.containsKey("flameforge.command.station.remove"));
        assertTrue(perms.containsKey("flameforge.command.station.list"));
        assertTrue(perms.containsKey("flameforge.command.station.info"));
        assertTrue(perms.containsKey("flameforge.command.station.teleport"));
        assertTrue(perms.containsKey("flameforge.command.setup.tier"));
        assertTrue(perms.containsKey("flameforge.bypass.cost"));
        assertTrue(perms.containsKey("flameforge.bypass.cooldown"));
        assertTrue(perms.containsKey("flameforge.admin"));
    }

    private PluginDescriptionFile loadPluginDescription() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream, "plugin.yml should be loadable from classpath");
        try {
            return new PluginDescriptionFile(stream);
        } catch (InvalidDescriptionException e) {
            throw new AssertionError("plugin.yml should be valid", e);
        }
    }
}
