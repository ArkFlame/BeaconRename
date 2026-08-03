package com.arkflame.flameforge.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTemplateLoaderTest {

    @Test
    void flatten_recursivelyCreatesDottedScalarAndListEntries() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("root.message", "Hello");
        yaml.set("root.nested.number", 42);
        yaml.set("root.nested.enabled", true);
        yaml.set("root.nested.lines", Arrays.asList("one", "two"));

        Map<String, Map<String, Object>> flattened = MessageTemplateLoader.flatten(yaml);

        assertEquals("Hello", flattened.get("root.message").get("message"));
        assertEquals("42", flattened.get("root.nested.number").get("message"));
        assertEquals("true", flattened.get("root.nested.enabled").get("message"));
        assertEquals(Arrays.asList("one", "two"), flattened.get("root.nested.lines").get("lines"));
        assertFalse(flattened.containsKey("root"));
        assertFalse(flattened.containsKey("root.nested"));
    }

    @Test
    void flatten_returnsImmutableDefensiveResults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("message", "original");
        yaml.set("lines", Arrays.asList("one", "two"));

        Map<String, Map<String, Object>> flattened = MessageTemplateLoader.flatten(yaml);
        yaml.set("message", "changed");

        assertEquals("original", flattened.get("message").get("message"));
        assertThrows(UnsupportedOperationException.class,
            () -> flattened.put("other", flattened.get("message")));
        assertThrows(UnsupportedOperationException.class,
            () -> flattened.get("message").put("message", "changed"));

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) flattened.get("lines").get("lines");
        assertThrows(UnsupportedOperationException.class, () -> lines.add("three"));
        assertTrue(lines.equals(Arrays.asList("one", "two")));
    }
}
