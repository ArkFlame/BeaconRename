package com.arkflame.flameforge.resources;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
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
