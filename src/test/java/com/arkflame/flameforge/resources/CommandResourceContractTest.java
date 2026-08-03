package com.arkflame.flameforge.resources;

import com.arkflame.flameforge.command.CommandNode;
import com.arkflame.flameforge.config.MessageTemplateLoader;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CommandResourceContractTest {

    @Test
    void messagesContainEveryCommandDescriptionHelpAndResultKey() {
        Map<String, Map<String, Object>> messages = loadMessages();
        Set<String> descriptionKeys = new LinkedHashSet<>();
        Set<String> categoryKeys = new LinkedHashSet<>();
        Set<String> helpKeys = new LinkedHashSet<>();

        for (CommandNode node : CommandNode.values()) {
            assertTrue(node.getDescriptionKey().isPresent(), node.name() + " must have description key");
            descriptionKeys.add(node.getDescriptionKey().get());
            categoryKeys.add("help.categories." + categoryKey(node.getCategory()));
        }

        helpKeys.addAll(Arrays.asList(
            "help.border", "help.root-header", "help.group-header", "help.entry", "help.hover",
            "help.empty", "help.unknown-path"
        ));

        assertFalse(descriptionKeys.isEmpty(), "CommandNode descriptions must be covered non-vacuously");
        assertFalse(categoryKeys.isEmpty(), "CommandNode categories must be covered non-vacuously");
        assertFalse(helpKeys.isEmpty(), "Help keys must be covered non-vacuously");

        for (String key : descriptionKeys) {
            assertMessage(messages, key);
        }
        for (String key : categoryKeys) {
            assertMessage(messages, key);
        }
        for (String key : helpKeys) {
            assertMessage(messages, key);
        }

        List<String> resultKeys = Arrays.asList(
            "open.menu-opened", "preview.result", "tp.success", "station-add.success",
            "station-teleport.success", "command.unknown",
            "help.no-permission", "open.no-permission", "open.player-only"
        );
        for (String key : resultKeys) {
            assertMessage(messages, key);
        }
    }

    @Test
    void allScalarMessageTemplatesParseWithoutFormatError() {
        Map<String, Map<String, Object>> messages = loadMessages();
        TextRenderer renderer = new TextRenderer();
        int templateCount = 0;

        for (Map.Entry<String, Map<String, Object>> entry : messages.entrySet()) {
            Object message = entry.getValue().get("message");
            if (message instanceof String) {
                Component rendered = renderer.render((String) message, Collections.emptyMap(),
                    Collections.emptyMap(), entry.getKey(), Logger.getLogger("message-contract"));
                assertNotNull(rendered, entry.getKey() + " should render");
                assertFalse(renderer.toLegacy(rendered).contains("Message format error"),
                    entry.getKey() + " should not produce format error");
                templateCount++;
            }
        }

        assertTrue(templateCount > 100, "Source messages must provide substantial scalar template coverage");
    }

    @Test
    void helpResourcesArePagelessAndContainNoNavigationKeys() {
        Map<String, Map<String, Object>> messages = loadMessages();

        assertFalse(messages.containsKey("help.previous-enabled"), "messages should not have help.previous-enabled");
        assertFalse(messages.containsKey("help.previous-disabled"), "messages should not have help.previous-disabled");
        assertFalse(messages.containsKey("help.next-enabled"), "messages should not have help.next-enabled");
        assertFalse(messages.containsKey("help.next-disabled"), "messages should not have help.next-disabled");
        assertFalse(messages.containsKey("help.page"), "messages should not have help.page");
        assertFalse(messages.containsKey("help.total_pages"), "messages should not have help.total_pages");

        assertTrue(messages.containsKey("help.root-header"), "messages should have help.root-header");
        assertTrue(messages.containsKey("help.group-header"), "messages should have help.group-header");
        assertTrue(messages.containsKey("help.entry"), "messages should have help.entry");
        assertTrue(messages.containsKey("help.hover"), "messages should have help.hover");
    }

    private Map<String, Map<String, Object>> loadMessages() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("messages.yml");
        assertNotNull(stream, "messages.yml should be loadable from classpath");
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return MessageTemplateLoader.flatten(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception failure) {
            throw new AssertionError("messages.yml should load", failure);
        }
    }

    private void assertMessage(Map<String, Map<String, Object>> messages, String key) {
        assertMessage(messages, key, key);
    }

    private void assertMessage(Map<String, Map<String, Object>> messages, String key, String label) {
        Map<String, Object> settings = messages.get(key);
        assertNotNull(settings, label + " must exist in messages.yml");
        Object message = settings.get("message");
        assertTrue(message instanceof String && !((String) message).trim().isEmpty(),
            label + " must be a non-empty scalar message");
    }

    private String categoryKey(CommandNode.Category category) {
        return category.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
