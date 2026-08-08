package com.arkflame.flameforge.resources;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PluginResourceContractTest {

    @Test
    void sourceDescriptorUsesMavenPlaceholderAndScalarAuthor() throws Exception {
        File basedir = new File(System.getProperty("user.dir"));
        File pluginYml = new File(basedir, "src/main/resources/plugin.yml");

        assertTrue(pluginYml.exists(), "plugin.yml should exist at ${project.basedir}/src/main/resources/plugin.yml");

        InputStream fis = new FileInputStream(pluginYml);
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(fis);
            assertTrue(loaded instanceof Map, "plugin.yml should load as a map");
            @SuppressWarnings("unchecked")
            Map<?, ?> pluginData = (Map<?, ?>) loaded;

            Object versionObj = pluginData.get("version");
            assertNotNull(versionObj, "version should not be null");
            assertEquals("${project.version}", versionObj, "version should be Maven placeholder ${project.version}");

            Object authorObj = pluginData.get("author");
            assertNotNull(authorObj, "author should not be null");
            assertTrue(authorObj instanceof String, "author should be scalar String, not list");
            assertEquals("ArkFlame Studios", authorObj, "author should be 'ArkFlame Studios'");
        } finally {
            fis.close();
        }
    }

    @Test
    void filteredDescriptorMatchesPomVersionAndLoadsMainNameAuthor() throws Exception {
        File basedir = new File(System.getProperty("user.dir"));
        File pomFile = new File(basedir, "pom.xml");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document pomDoc = builder.parse(pomFile);
        pomDoc.getDocumentElement().normalize();

        org.w3c.dom.NodeList versionNodes = pomDoc.getElementsByTagName("version");
        String pomVersion = null;
        for (int i = 0; i < versionNodes.getLength(); i++) {
            org.w3c.dom.Node node = versionNodes.item(i);
            if (node.getParentNode().getNodeName().equals("project")) {
                pomVersion = node.getTextContent();
                break;
            }
        }
        assertNotNull(pomVersion, "pom.xml should have project/version");

        InputStream classpathStream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(classpathStream, "plugin.yml should be loadable from classpath");
        PluginDescriptionFile pdf;
        try {
            pdf = new PluginDescriptionFile(classpathStream);
        } finally {
            classpathStream.close();
        }

        assertNotNull(pdf.getVersion(), "Plugin version should not be null");
        assertEquals(pomVersion, pdf.getVersion(), "Filtered descriptor version should match pom project/version");
        assertNotEquals("${project.version}", pdf.getVersion(), "Filtered descriptor version should not be Maven placeholder");

        assertNotNull(pdf.getMain(), "Plugin main should not be null");
        assertEquals("com.arkflame.flameforge.FlameForgePlugin", pdf.getMain(), "Plugin main class should be exact");

        assertNotNull(pdf.getName(), "Plugin name should not be null");
        assertEquals("FlameForge", pdf.getName(), "Plugin name should be exact");

        List<String> authors = pdf.getAuthors();
        assertNotNull(authors, "Authors should not be null");
        assertTrue(authors.contains("ArkFlame Studios"), "Authors should contain 'ArkFlame Studios'");
    }

    @Test
    void descriptorDeclaresAliasesFoliaAndExactSoftDependencies() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream, "plugin.yml should be loadable from classpath");

        org.bukkit.configuration.file.YamlConfiguration yaml =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        Object aliasesObj = yaml.get("commands.flameforge.aliases");
        assertNotNull(aliasesObj, "Command aliases should not be null");

        boolean hasForge = false;
        boolean hasFf = false;

        if (aliasesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> aliases = (List<String>) aliasesObj;
            hasForge = aliases.contains("forge");
            hasFf = aliases.contains("ff");
        } else if (aliasesObj instanceof String) {
            String aliases = (String) aliasesObj;
            hasForge = aliases.contains("forge");
            hasFf = aliases.contains("ff");
        }
        assertTrue(hasForge, "Should have 'forge' alias");
        assertTrue(hasFf, "Should have 'ff' alias");

        Object foliaSupported = yaml.get("folia-supported");
        assertNotNull(foliaSupported, "folia-supported should be defined");
        assertEquals(true, foliaSupported, "folia-supported should be true");

        List<String> softdepends = yaml.getStringList("softdepend");
        assertNotNull(softdepends, "Soft depends should not be null");
        assertEquals(3, softdepends.size(), "Should have exactly 3 soft depends");
        assertTrue(softdepends.contains("Vault"), "Should soft-depend on Vault");
        assertTrue(softdepends.contains("FancyHolograms"), "Should soft-depend on FancyHolograms");
        assertTrue(softdepends.contains("DecentHolograms"), "Should soft-depend on DecentHolograms");

        InputStream stationsStream = getClass().getClassLoader().getResourceAsStream("stations.yml");
        assertNull(stationsStream, "classpath stations.yml should be absent");

        InputStream profilesStream = getClass().getClassLoader().getResourceAsStream("station-profiles.yml");
        assertNotNull(profilesStream, "classpath station-profiles.yml should be present");
        try { profilesStream.close(); } catch (IOException ignored) {}

        InputStream configStream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(configStream, "config.yml should be present on classpath");
        org.bukkit.configuration.file.YamlConfiguration configYaml =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new InputStreamReader(configStream, StandardCharsets.UTF_8));
        try { configStream.close(); } catch (IOException ignored) {}
        assertNull(configYaml.get("tier-migration"), "config.yml should have no tier-migration path");

        String[] tierFiles = {"tier1.yml", "tier2.yml", "tier3.yml", "tier4.yml", "tier5.yml", "tier6.yml", "tier7.yml"};
        for (String tierFile : tierFiles) {
            InputStream tierStream = getClass().getClassLoader().getResourceAsStream("tiers/" + tierFile);
            assertNotNull(tierStream, "tiers/" + tierFile + " should be bundled");
            org.bukkit.configuration.file.YamlConfiguration tierYaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new InputStreamReader(tierStream, StandardCharsets.UTF_8));
            try { tierStream.close(); } catch (IOException ignored) {}
            assertEquals(2, tierYaml.getInt("schema-version"),
                "bundled " + tierFile + " should have schema-version 2");
        }
    }

    @Test
    void packagedMenuAndPowerResourcesFollowContract() throws Exception {
        InputStream menuStream = getClass().getClassLoader().getResourceAsStream("menus.yml");
        assertNotNull(menuStream, "menus.yml should be present on classpath");
        org.bukkit.configuration.file.YamlConfiguration menuYaml =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new InputStreamReader(menuStream, StandardCharsets.UTF_8));
        try { menuStream.close(); } catch (IOException ignored) {}

        org.bukkit.configuration.ConfigurationSection defaultMenu = menuYaml.getConfigurationSection("default");
        assertNotNull(defaultMenu, "default menu section should exist");

        org.bukkit.configuration.ConfigurationSection slotsSection = defaultMenu.getConfigurationSection("slots");
        assertNotNull(slotsSection, "slots section should exist");
        Set<String> slotKeys = slotsSection.getKeys(false);
        assertEquals(2, slotKeys.size(), "menu slots should contain exactly 2 interactive positions");
        assertTrue(slotKeys.contains("input"), "slots should contain input");
        assertTrue(slotKeys.contains("confirm"), "slots should contain confirm");

        String[] tierFiles = {"tier1.yml", "tier2.yml", "tier3.yml", "tier4.yml", "tier5.yml", "tier6.yml", "tier7.yml"};
        for (String tierFile : tierFiles) {
            InputStream tierStream = getClass().getClassLoader().getResourceAsStream("tiers/" + tierFile);
            assertNotNull(tierStream, "tiers/" + tierFile + " should be bundled");
            org.bukkit.configuration.file.YamlConfiguration tierYaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new InputStreamReader(tierStream, StandardCharsets.UTF_8));
            try { tierStream.close(); } catch (IOException ignored) {}

            List<?> variants = tierYaml.getList("variants");
            if (variants != null) {
                for (Object variantObj : variants) {
                    if (variantObj instanceof Map) {
                        Map<?, ?> variant = (Map<?, ?>) variantObj;
                        Object powersObj = variant.get("powers");
                        if (powersObj instanceof List) {
                            List<?> powers = (List<?>) powersObj;
                            for (Object powerObj : powers) {
                                if (powerObj instanceof Map) {
                                    Map<?, ?> power = (Map<?, ?>) powerObj;
                                    assertTrue(power.containsKey("id"), "power should have id key");
                                    assertTrue(power.containsKey("type"), "power should have type key");
                                }
                            }
                        }
                    }
                }
            }
        }

        String[] menuKeys = {"close", "prev", "next", "page", "prompt", "input-other", "confirm-secondary"};
        for (String key : menuKeys) {
            Object deprecated = menuYaml.get("default.items." + key);
            assertNull(deprecated, "menu should not contain obsolete control: " + key);
        }
    }

    @Test
    void permissionGraphContainsEveryCommandPermissionAndExactAdminChildrenWithoutBypasses() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream, "plugin.yml should be loadable from classpath");

        Object rootObj;
        try (InputStream autoClose = stream) {
            rootObj = new Yaml().load(new InputStreamReader(autoClose, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("Failed to load plugin.yml", e);
        }

        assertTrue(rootObj instanceof Map, "plugin.yml root should be a map");
        Map<?, ?> root = (Map<?, ?>) rootObj;

        Object permissionsObj = root.get("permissions");
        assertTrue(permissionsObj instanceof Map, "Permissions should be a map");
        Map<?, ?> permissions = (Map<?, ?>) permissionsObj;

        Set<String> expectedCommandPermissions = new HashSet<>(Arrays.asList(
            "flameforge.use",
            "flameforge.command.help",
            "flameforge.command.open",
            "flameforge.command.open.others",
            "flameforge.command.reload",
            "flameforge.command.validate",
            "flameforge.command.tiers",
            "flameforge.command.tier.info",
            "flameforge.command.preview",
            "flameforge.command.history",
            "flameforge.command.history.others",
            "flameforge.command.station.add",
            "flameforge.command.station.remove",
            "flameforge.command.station.list",
            "flameforge.command.station.info",
            "flameforge.command.station.teleport",
            "flameforge.command.setup.tier"
        ));

        for (String perm : expectedCommandPermissions) {
            assertTrue(permissions.containsKey(perm), "Permission should exist: " + perm);
        }

        Object adminObj = permissions.get("flameforge.admin");
        assertTrue(adminObj instanceof Map, "flameforge.admin permission should be a map");
        Map<?, ?> admin = (Map<?, ?>) adminObj;

        Object childrenObj = admin.get("children");
        assertTrue(childrenObj instanceof Map, "flameforge.admin children should be a map");
        Map<?, ?> actualChildren = (Map<?, ?>) childrenObj;

        Set<String> expected = new HashSet<>(Arrays.asList(
            "flameforge.use",
            "flameforge.command.help",
            "flameforge.command.open",
            "flameforge.command.open.others",
            "flameforge.command.reload",
            "flameforge.command.validate",
            "flameforge.command.tiers",
            "flameforge.command.tier.info",
            "flameforge.command.preview",
            "flameforge.command.history",
            "flameforge.command.history.others",
            "flameforge.command.station.add",
            "flameforge.command.station.remove",
            "flameforge.command.station.list",
            "flameforge.command.station.info",
            "flameforge.command.station.teleport",
            "flameforge.command.setup.tier"
        ));

        assertEquals(expected, actualChildren.keySet(),
            "flameforge.admin children should match exactly");
        for (String permission : expected) {
            assertEquals(Boolean.TRUE, actualChildren.get(permission),
                "Admin child permission must be enabled: " + permission);
        }
        assertFalse(actualChildren.containsKey("flameforge.bypass.cost"),
            "Admin children must not include cost bypass");
        assertFalse(actualChildren.containsKey("flameforge.bypass.cooldown"),
            "Admin children must not include cooldown bypass");
    }
}
